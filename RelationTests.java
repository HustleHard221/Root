// Tests unitaires de la classe Relation.

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class RelationTests {
	
	// Méthode principale exécutant le scénario complet de test.
    public static void main(String[] args) throws Exception {
        new File("DBRel").mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter("rel.cfg"))) {
            pw.println("dbpath=DBRel\npagesize=1024\ndm_maxfilecount=5\nbm_buffercount=5\nbm_policy=LRU");
        }
        DBConfig c = DBConfig.LoadDBConfig("rel.cfg");
        
        DiskManager dm = new DiskManager(c); dm.Init();
        BufferManager bm = new BufferManager(c, dm);
        
        PageId h = dm.AllocPage();
        ByteBuffer b = bm.GetPage(h);
        b.putInt(0,-1); b.putInt(4,-1); b.putInt(8,-1); b.putInt(12,-1);
        bm.FreePage(h, true);
        
        Relation r = new Relation("T", c, dm, bm, h);
        r.addColumn("A", "INT");
        r.initSchema();
        
        Record rec = new Record(); rec.addValeurs(42);
        RecordId rid = r.InsererRecord(rec);
        
        ArrayList<Record> res = r.GetTousRecords();
        if (res.size() != 1 || !res.get(0).getValeurs().get(0).equals(42)) throw new Exception("Error");
        
        r.SupprimerRecord(rid);
        
        if (r.GetTousRecords().size() != 0) throw new Exception("Erreur de suppression");
        
        r.getPagesDonnees();

        dm.Finish();
        System.out.println("Tests de la classe Relation réussis.");
    }
}