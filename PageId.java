import java.util.Objects;

public class PageId {
    private int FileIdx;
    private int PageIdx;

    public PageId(int fileIdx, int pageIdx) {
        this.FileIdx = fileIdx;
        this.PageIdx = pageIdx;
    }

    public int getFileIdx() { return FileIdx; }
    public int getPageIdx() { return PageIdx; }

    @Override
    public String toString() { return "PageId(Fichier=" + FileIdx + ", Page=" + PageIdx + ")"; }

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