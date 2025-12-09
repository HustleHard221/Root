import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;

public class BufferManagerTests {
    public static void main(String[] args) throws Exception {
        // Etape 1 : Préparation du dossier DB et de la config
    	new File("DBTestBM").mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter("bm.cfg"))) {
            pw.println("dbpath=DBTestBM\npagesize=4\ndm_maxfilecount=5\nbm_buffercount=2\nbm_policy=LRU");
        }
        
        // Etape 2 : Initialisation gestionnaires de disque et mémoire tampon
        DBConfig c = DBConfig.LoadDBConfig("bm.cfg");
        DiskManager diskmanager = new DiskManager(c); diskmanager.Init();
        BufferManager buffermanager = new BufferManager(c, diskmanager);
       
        // Etape 3 : Allocation et liberation
        PageId p1 = diskmanager.AllocPage();
        buffermanager.GetPage(p1);
        buffermanager.FreePage(p1, false);
       
        // Etape 4 : Nettoyage et fin
        diskmanager.Finish();
        System.out.println("Tests de BufferManager: OK");
    }
}