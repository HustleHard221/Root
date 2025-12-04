import java.util.Objects;

// Classe attribuant un identifiant unique pour une page
public class PageId {
    
	// Identifiant fichier
	private int FileIdx;
    
	// Rang page dans un fichier
	private int PageIdx;

	// Constructeur
    public PageId(int fileIdx, int pageIdx) {
        this.FileIdx = fileIdx;
        this.PageIdx = pageIdx;
    }

    // Getters
    public int getFileIdx() { return FileIdx; }
    public int getPageIdx() { return PageIdx; }

    // Gestion affichage
    @Override
    public String toString() { return "PageId(Fichier=" + FileIdx + ", Page=" + PageIdx + ")"; }

    // Comparaison objets PageId
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PageId pageId = (PageId) obj;
        return FileIdx == pageId.FileIdx && PageIdx == pageId.PageIdx;
    }

    @Override
    public int hashCode() { return Objects.hash(FileIdx, PageIdx); }
}
