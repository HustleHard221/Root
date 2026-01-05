import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;

public class DiskManagerTests {
    public static void main(String[] args) throws Exception {
    	// Création du dossier qui contient les fichiers de la base de données
    	new File("DBTest").mkdirs();
        
    	// Création d'un fichier de config pour le test
    	try (PrintWriter pw = new PrintWriter(new FileWriter("dm.cfg"))) {
            pw.println("dbpath=DBTest\npagesize=4\ndm_maxfilecount=5\nbm_buffercount=5\nbm_policy=LRU");
        }
        
    	// Chargement de la config
    	DBConfig c = DBConfig.LoadDBConfig("dm.cfg");
        
    	DiskManager dm = new DiskManager(c);
        dm.Init();
        PageId p = dm.AllocPage();
        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt(123);
        dm.WritePage(p, b);
        dm.ReadPage(p, b);
        dm.Finish();
        System.out.println("Tests de DiskManager réussis");
    }
}