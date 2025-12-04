import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

// DiskManager s'occupe de l'allocation des pages et des opérations de lecture et écriture.
 
public class DiskManager {
    private DBConfig config;
    
    // Liste fichiers ouverts (évite de les rouvrir)
    private ArrayList<RandomAccessFile> fichiersDonnees;
    
    // Contient identifiants pages supprimées pour les réutiliser
    private ArrayList<PageId> pagesLibres;
    
    // Tableau pour compter nombre de pages dans les fichiers 
    private int[] pagesAllouees;
   
    // Prochain fichier a utiliser
    private int prochaineAlloc;

    public DiskManager(DBConfig config) {
        this.config = config;
        this.fichiersDonnees = new ArrayList<>();
        this.pagesLibres = new ArrayList<>();
        this.prochaineAlloc = 0;
    }

    public void Init() throws IOException {
        this.pagesAllouees = new int[config.getDm_maxfilecount()];
        File dossierBinaire = new File(config.getDbpath(), "BinData");
        if (!dossierBinaire.exists()) dossierBinaire.mkdirs();

        for (int i = 0; i < config.getDm_maxfilecount(); i++) {
            File file = new File(dossierBinaire, "Data" + i + ".bin");
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            fichiersDonnees.add(raf);
            this.pagesAllouees[i] = (int) (raf.length() / config.getPagesize());
        }
    }

    public void Finish() throws IOException {
        for (RandomAccessFile raf : fichiersDonnees) raf.close();
        fichiersDonnees.clear();
        pagesLibres.clear();
        pagesAllouees = null;
    }

    public PageId AllocPage() throws IOException {
        if (!pagesLibres.isEmpty()) return pagesLibres.remove(0);

        int fileIdx = prochaineAlloc;
        RandomAccessFile file = fichiersDonnees.get(fileIdx);
        int nouvellePageIdx = pagesAllouees[fileIdx];
        
        long newLength = (long)(nouvellePageIdx + 1) * config.getPagesize();
        file.setLength(newLength);

        pagesAllouees[fileIdx]++;
        prochaineAlloc = (prochaineAlloc + 1) % config.getDm_maxfilecount();

        return new PageId(fileIdx, nouvellePageIdx);
    }

    public void DeallocPage(PageId pageId) {
        if (!pagesLibres.contains(pageId)) pagesLibres.add(pageId);
    }

   
    // Lit contenu d'une page disque vers un buffer mémoire
    public void ReadPage(PageId pageId, ByteBuffer buff) throws IOException {
        buff.clear();
        RandomAccessFile file = fichiersDonnees.get(pageId.getFileIdx());
        FileChannel channel = file.getChannel();
        long offset = (long)pageId.getPageIdx() * config.getPagesize();
        if (channel.read(buff, offset) != config.getPagesize()) {
            throw new IOException("Lecture incomplète page " + pageId);
        }
    }

    // Écrit contenu d'un buffer mémoire vers une page disque
    public void WritePage(PageId pageId, ByteBuffer buff) throws IOException {
        buff.rewind();
        RandomAccessFile file = fichiersDonnees.get(pageId.getFileIdx());
        FileChannel channel = file.getChannel();
        
        long offset = (long)pageId.getPageIdx() * config.getPagesize();
       
        
        if (channel.write(buff, offset) != config.getPagesize()) {
            throw new IOException("Écriture incomplète page " + pageId);
        }
    }
}