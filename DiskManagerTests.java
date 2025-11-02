package projet;

import java.io.File;
import java.util.Arrays;

//Classe de test pour le DiskManager.
public class DiskManagerTests {

    // Configuration de Test
    private static final int TEST_PAGE_SIZE = 4; // 4 octets
    
    // On utilise un dossier de base de données séparé pour les tests
    private static final String TEST_DB_PATH = "../DB_TEST";
    
    private static DBConfig config;
    private static DiskManager diskManager;

  
    public static void main(String[] args) {
        System.out.println("--- DÉMARRAGE DES TESTS DU DISKMANAGER ---");

       //Nettoyer l'environnement de test avant de commencer (Supprime l'ancien dossier DB_TEST s'il existe)
        cleanup(); 
        

        config = new DBConfig(TEST_DB_PATH, TEST_PAGE_SIZE, 10);

        try {
            testSimpleReadWrite();
            testPersistenceAndRecycling();
            
            System.out.println("\n[SUCCÈS] Tous les tests du DiskManager sont passés !");

        } catch (Exception e) {
            // Si un test échoue, on attrape l'erreur ici
            System.err.println("\n[ERREUR] Un test a échoué : " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 4. Nettoyer l'environnement de test après l'exécution
            cleanup();
        }
    }

    //Test 1 : Alloue une page, y écrit, la relit et compare.
  
    public static void testSimpleReadWrite() throws Exception {
        System.out.println("\n[Test] Démarrage testSimpleReadWrite...");
        
        // On crée un nouveau DiskManager et on l'initialise
        diskManager = new DiskManager(config);
        diskManager.Init(); // Crée le dossier DB_TEST/BinData/

        // Allouer une page. Ce sera (0,0)
        PageId page1 = diskManager.AllocPage();
        System.out.println("  Page allouée : " + page1);

        //Préparer un buffer à écrire (de 4 octets)
        byte[] dataAEcrire = new byte[]{ 0x0A, 0x0B, 0x0C, 0x0D };
        System.out.println("  Données à écrire : " + Arrays.toString(dataAEcrire));

        // Écrire sur la page
        diskManager.WritePage(page1, dataAEcrire);

        //Préparer un buffer de lecture (vide, de 4 octets)
        byte[] dataLu = new byte[TEST_PAGE_SIZE];

        //Relire la page qu'on vient d'écrire
        diskManager.ReadPage(page1, dataLu);
        System.out.println("  Données lues :     " + Arrays.toString(dataLu));

        
        if (!Arrays.equals(dataAEcrire, dataLu)) {
            throw new Exception("Les données lues ne correspondent pas aux données écrites !");
        }
        
        // finish le DiskManager
        diskManager.Finish();
        System.out.println("  [OK] testSimpleReadWrite validé.");
    }
    
    //Test 2 : Vérifie que Init/Finish/Dealloc fonctionnent ensemble. Simule un redémarrage du SGBD.
    public static void testPersistenceAndRecycling() throws Exception {
        System.out.println("\n[Test] Démarrage testPersistenceAndRecycling...");

        // Phase 1: Allouer, Désallouer, et Éteindre
        System.out.println("  Phase 1 : Allocation et désallocation...");
        diskManager = new DiskManager(config);
        diskManager.Init(); // Init sur un dossier PROPRE

        PageId p1 = diskManager.AllocPage(); // (0,0)
        PageId p2 = diskManager.AllocPage(); // (0,1) //On va libérer celle-ci
        PageId p3 = diskManager.AllocPage(); // (0,2)
        
        System.out.println("  Pages allouées : " + p1 + ", " + p2 + ", " + p3);

        // On libère la page du milieu
        diskManager.DeallocPage(p2);
        System.out.println("  Page désallouée : " + p2);
        
        // On éteint. Finish() doit sauvegarder la freePagesList avec (0,1) dedans.
        diskManager.Finish();
        System.out.println("  DiskManager arrêté. freePages.list doit être sauvegardée.");


        // Phase 2: Redémarrer et Vérifier le Recyclage
        System.out.println("  Phase 2 : Redémarrage et vérification du recyclage...");
        
        // On crée un NOUVEL objet DiskManager pour simuler un redémarrage
        DiskManager newDiskManager = new DiskManager(config);
        newDiskManager.Init(); // Doit lire le dossier ET freepages.list
        
        // Le premier appel à AllocPage DOIT maintenant retourner la page recyclée
        PageId recycledPage = newDiskManager.AllocPage();
        System.out.println("  Page allouée au redémarrage : " + recycledPage);
        
        // VÉRIFICATION 1
        if (!recycledPage.equals(p2)) {
            throw new Exception("Erreur de persistance ! La page recyclée attendue était " 
                                + p2 + " mais on a eu " + recycledPage);
        }
        
        // Le prochain appel doit allouer une nouvelle page
        // (car la liste de recyclage est de nouveau vide)
        PageId newPage = newDiskManager.AllocPage();
        System.out.println("  Prochaine page allouée : " + newPage);

        // VÉRIFICATION 2 
        PageId expectedNewPage = new PageId(0, 4);
        if (!newPage.equals(expectedNewPage)) {
            throw new Exception("Erreur de logique d'allocation ! La nouvelle page attendue était " 
                                + expectedNewPage + " mais on a eu " + newPage);
        }

        newDiskManager.Finish();
        System.out.println("  [OK] Persistance et recyclage validés.");
    }
    
    //Méthode utilitaire pour nettoyer (supprimer) le dossier de test et tout son contenu.    
    private static void cleanup() {
        File dbDir = new File(TEST_DB_PATH);
        if (dbDir.exists()) {
            System.out.println("Nettoyage du dossier de test : " + dbDir.getAbsolutePath());
            deleteDirectory(dbDir);
        }
    }

    //Méthode utilitaire récursive pour supprimer un dossier.
    private static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file); // Appel récursif
            }
        }
        return directoryToBeDeleted.delete(); // Supprime le dossier (ou fichier)
    }
}
