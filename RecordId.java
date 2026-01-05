// Classe gérant les adresses permettant de localiser un enregistrement précis sur le disque.

import java.util.Objects;

public class RecordId {
    private PageId pageId;
    private int slotIdx;
    
    // Constructeur
    public RecordId(PageId pageId, int slotIdx) {
        this.pageId = pageId;
        this.slotIdx = slotIdx;
    }
    
    // Getters
    public PageId getPageId() { return pageId; }
    public int getSlotIdx() { return slotIdx; }

    @Override
    // Méthode renvoyant une description textuelle.
    public String toString() { return "RecordId(" + pageId + ", Slot=" + slotIdx + ")"; }

    @Override
    // Methode vérifiant si un identifiant pointe vers le même emplacement physique qu'un autre. 
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RecordId recordId = (RecordId) obj;
        return slotIdx == recordId.slotIdx && Objects.equals(pageId, recordId.pageId);
    }

    @Override
    // Méthode générant une combinaison pageId/slotIdx afin de les stocker dans des hashmap
    public int hashCode() { return Objects.hash(pageId, slotIdx); }
}