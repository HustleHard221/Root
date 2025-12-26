// Tests de la classe DBManager 

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;

public class DBManagerTests {
    
	// Méthode principale gérant les tests.
	public static void main(String[] args) throws Exception {
        new File("DBManagertest").mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter("mgr.cfg"))) {
            pw.println("dbpath=DBManagertest\npagesize=1024\ndm_maxfilecount=5\nbm_buffercount=5\nbm_policy=LRU");
        }
        DBConfig c = DBConfig.LoadDBConfig("mgr.cfg");
        DiskManager dm = new DiskManager(c); dm.Init();
        BufferManager bm = new BufferManager(c, dm);
        DBManager dbm = new DBManager(c, dm, bm);
        
        PageId h = dm.AllocPage();
        ByteBuffer b = bm.GetPage(h);
        b.putInt(0,-1); b.putInt(4,-1); b.putInt(8,-1); b.putInt(12,-1);
        bm.FreePage(h, true);
        
        Relation r = new Relation("T", c, dm, bm, h);
        r.addColumn("A", "INT"); r.initSchema();
        dbm.AddTable(r);
        
        dbm.RemoveTable("T");
        dm.Finish();
        System.out.println("Tests de DBManager: OK");
    }
}