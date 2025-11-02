package projet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.LinkedList; 
import java.util.Queue;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DiskManager {

    private DBConfig config;
    
    //Liste des fichiers de données (DataX.bin) ouverts.
    private ArrayList<RandomAccessFile> filePointers;
    
   //File d'attente des PageId des pages qui ont été désallouées.
    private Queue<PageId> freePagesList;
    
    private String binDataPath;

    public DiskManager(DBConfig config) {
        this.config = config;
    }
    
    /*
     * Alloue une page, soit en réutilisant une page libre, soit en en créant une nouvelle.
     *
     * @return Le PageId de la page allouée.
     * @throws IOException Si l'allocation échoue (disque plein, max fichiers atteints).
     */
    public PageId AllocPage() throws IOException {
        
        if (!freePagesList.isEmpty()) {
            return freePagesList.poll(); 
        }

        
        int fileIdxToUse;
        RandomAccessFile fileToUse;

        if (filePointers.isEmpty()) {
            // On vérifie qu'on a le droit de créer au moins un fichier
            if (config.getDmMaxFileCount() <= 0) {
                 throw new IOException("Configuration 'dm_maxfilecount' est 0, impossible de créer un fichier.");
            }
            // On crée le tout premier fichier : Data0.bin
            fileIdxToUse = 0;
            fileToUse = createNewFile(fileIdxToUse);
        } else {
            // Cas ou Des fichiers existent. On prend le dernier.
            fileIdxToUse = filePointers.size() - 1;
            fileToUse = filePointers.get(fileIdxToUse);

            // On calcule le nombre de pages actuelles dans ce fichier
            long currentPageCount = fileToUse.length() / config.getPageSize();
            
            // Cas ou Le dernier fichier est "plein" 
            if (currentPageCount >= Integer.MAX_VALUE) {
                // On doit créer un nouveau fichier
                
                // On vérifie si on n'a pas atteint le nombre max de fichiers
                if (filePointers.size() >= config.getDmMaxFileCount()) {
                    throw new IOException("Nombre maximum de fichiers (" + config.getDmMaxFileCount() + ") atteint, impossible d'allouer une nouvelle page.");
                }
                
                // On peut créer un nouveau fichier
                fileIdxToUse = filePointers.size(); // Le nouvel index (ex: 0 -> 1)
                fileToUse = createNewFile(fileIdxToUse);
            }
        }

     //On calcule l'index de la future nouvelle page.
        int newPageIdx = (int) (fileToUse.length() / config.getPageSize());

        // On calcule la nouvelle taille que le fichier doit avoir.
        long newLength = fileToUse.length() + config.getPageSize();

        fileToUse.setLength(newLength);

        return new PageId(fileIdxToUse, newPageIdx);
    }
    
    /*
     * Méthode privée (helper) pour créer un nouveau fichier DataX.bin
     * et l'ajouter à notre liste filePointers.
     * * @param fileIdx L'index du fichier à créer (ex: 0 pour Data0.bin)
     * @return Le fichier qui vient d'être créé et ouvert.
     * @throws IOException
     */
    private RandomAccessFile createNewFile(int fileIdx) throws IOException {
        String newFilePath = binDataPath + "Data" + fileIdx + ".bin";
        File f = new File(newFilePath);        
        RandomAccessFile raf = new RandomAccessFile(f, "rw");        
        filePointers.add(raf); 
        return raf;
    }
    
    /*
     * Lit le contenu d'une page sur le disque et le copie dans le buffer fourni.
     *
     * @param pageId L'adresse de la page à lire (FileIdx, PageIdx).
     * @param buff   Le buffer (alloué par l'appelant) qui recevra les données.
     * @throws IOException Si la lecture échoue (fichier non trouvé, page hors limites, etc.).
     */
    public void ReadPage(PageId pageId, byte[] buff) throws IOException {

        if (buff.length != config.getPageSize()) {
            throw new IllegalArgumentException("Le buffer fourni n'a pas la taille de page correcte. Attendu: "
                    + config.getPageSize() + ", Reçu: " + buff.length);
        }

        RandomAccessFile file = filePointers.get(pageId.getFileIdx());
        long offset = (long) pageId.getPageIdx() * config.getPageSize();
        file.seek(offset);
        file.readFully(buff);
    }
    
    /*
     * Écrit le contenu d'un buffer (depuis la RAM) sur une page spécifique du disque.
     *
     * @param pageId L'adresse de la page ou écrire (FileIdx, PageIdx).
     * @param buff   Le buffer (alloué et rempli par l'appelant) contenant les données.
     * @throws IOException Si l'écriture échoue (disque plein, etc.).
     */
    public void WritePage(PageId pageId, byte[] buff) throws IOException {

        if (buff.length != config.getPageSize()) {
            throw new IllegalArgumentException("Le buffer fourni n'a pas la taille de page correcte. Attendu: "
                    + config.getPageSize() + ", Reçu: " + buff.length);
        }

        RandomAccessFile file = filePointers.get(pageId.getFileIdx());
        long offset = (long) pageId.getPageIdx() * config.getPageSize();
        file.seek(offset);
        file.write(buff);
    }
    
    /*
     * Désalloue une page, la rendant disponible pour une future réutilisation.
     * L'implémentation consiste simplement à ajouter le PageId à la 
     * liste des pages libres (freePagesList).
     *
     * @param pageId L'identifiant de la page à désallouer (à recycler).
     */
    public void DeallocPage(PageId pageId) {
        if (!freePagesList.contains(pageId)) {
            freePagesList.add(pageId);          
        }
    }
    
    /*
     * Initialise le DiskManager au démarrage du SGBD.
     */
    public void Init() throws IOException {

        this.binDataPath = config.getDbpath() + File.separator + "BinData" + File.separator;
        Files.createDirectories(Paths.get(this.binDataPath));

        this.filePointers = new ArrayList<>();
        this.freePagesList = new LinkedList<>();

        // Ouvrir tous les fichiers DataX.bin qui existent déjà
        // On les ajoute à 'filePointers' pour que Read/WritePage fonctionnent
        int fileIdx = 0;
        while (fileIdx < config.getDmMaxFileCount()) {
            File file = new File(this.binDataPath + "Data" + fileIdx + ".bin");
            
            if (file.exists()) {
                RandomAccessFile raf = new RandomAccessFile(file, "rw");
                this.filePointers.add(raf);
                fileIdx++;
            } else {
                // Si Data[i].bin n'existe pas, on arrête de chercher
                break;
            }
        }

        // Charger la liste de recyclage (synchronisation)
        File freeListFile = new File(this.binDataPath + "freepages.list");
        
        if (freeListFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(freeListFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    
                    String[] parts = line.split(",");
                    int fIdx = Integer.parseInt(parts[0]);
                    int pIdx = Integer.parseInt(parts[1]);
                    this.freePagesList.add(new PageId(fIdx, pIdx));
                }
            }
        }
    }
    
    /*
     Termine le DiskManager, appelé à l'arrêt du SGBD.
     Sauvegarde l'état (pages libres) et ferme les fichiers.
     */
    public void Finish() throws IOException {

        // Sauvegarder la liste de recyclage (synchronisation)
        String freePagesFilePath = this.binDataPath + "freepages.list";
        

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(freePagesFilePath, false))) {
            
            // On parcourt la liste de recyclage en mémoire
            for (PageId pid : this.freePagesList) {
                // On écrit une ligne "FileIdx,PageIdx"
                writer.write(pid.getFileIdx() + "," + pid.getPageIdx());
                writer.newLine(); // Passe à la ligne suivante
            }
        }
        
        // Fermer tous les fichiers DataX.bin ouverts
        for (RandomAccessFile raf : this.filePointers) {
            if (raf != null) {
                raf.close();
            }
        }
    }
}