// Cette classe sert à lire séquentiellement tous les enregistrements d'une table.

import java.nio.ByteBuffer;

public class RelationScanner implements IRecordIterator {
    private Relation relation;
    private BufferManager bm;
    private PageId pageIdActuel;
    private int slotActuel;
    
    private RecordId dernierRecordId; 

    // Constructeur
    public RelationScanner(Relation rel, BufferManager bm) throws Exception {
        this.relation = rel;
        this.bm = bm;
        Reset();
    }

    // Réinitialise les curseurs de lecture pour recommencer le parcours de la table depuis le début.
    @Override
    public void Reset() throws Exception {
        this.pageIdActuel = null;
        this.slotActuel = -1;
    }
    
    private java.util.List<PageId> pages;
    private int indexListePages;

    // Parcourt les pages pour trouver et renvoyer le prochain enregistrement actif. 
    @Override
    public Record GetNextRecord() throws Exception {
        if (pages == null) {
            pages = relation.getPagesDonnees();
            indexListePages = 0;
            slotActuel = 0;
        }

        while (indexListePages < pages.size()) {
            PageId pid = pages.get(indexListePages);
            ByteBuffer buff = bm.GetPage(pid);
            
            try {
                int tailleEntete = 16;
                int maxSlots = relation.getNombreCases();
                
                for (int i = slotActuel; i < maxSlots; i++) {
                    if (buff.get(tailleEntete + i) == 1) {
                        Record r = new Record();
                        int pos = tailleEntete + maxSlots + (i * relation.getTailleRecord());
                        relation.readFromBuffer(r, buff, pos);
                        
                        dernierRecordId = new RecordId(pid, i);
                        slotActuel = i + 1;
                        return r;
                    }
                }
                slotActuel = 0;
                indexListePages++;
            } finally {
                bm.FreePage(pid, false);
            }
        }
        return null;
    }

    // Méthode requise par l'interface IRecordIterator.
    @Override
    public void Close() {
    }
    
    // Renvoie l'adresse précise (PageId + Slot) du dernier enregistrement qui vient d'être lu.
    public RecordId getLastRecordId() { return dernierRecordId; }
}