import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.io.IOException;
import java.util.List;

// Constructeur
public class Relation {

    private String nomRelation;
    private int numCol;
    private ArrayList<ColumnInfo> schema;

    private DBConfig config;
    private DiskManager diskManager;
    private BufferManager bufferManager;
    private PageId headerPageId;
    
    private int tailleRecord;
    private int nombreCases;

    private static final PageId NULL_PAGEID = new PageId(-1, -1);

    public Relation(String relName, DBConfig config, DiskManager dm, BufferManager bm, PageId headerPageId) {
        this.nomRelation = relName;
        this.config = config;
        this.diskManager = dm;
        this.bufferManager = bm;
        this.headerPageId = headerPageId;
        this.schema = new ArrayList<>();
        this.numCol = 0;
        this.tailleRecord = -1; 
        this.nombreCases = -1; 
    }

    // Crée un objet ColumnInfo pour l'ajouter à la liste schema
    public void addColumn(String colNom, String colType) {
        if (tailleRecord != -1) throw new IllegalStateException("Schema fixed");
        this.schema.add(new ColumnInfo(colNom, colType));
        this.numCol++;
    }

    // Calcule la taille d'un record et le nombre de cases par page.
    public void initSchema() {
        this.tailleRecord = calculateRecordSize();
        int overhead = 16; 
        if (tailleRecord + 1 > config.getPagesize() - overhead) {
            throw new IllegalStateException("Record too big");
        }
        this.nombreCases = (config.getPagesize() - overhead) / (tailleRecord + 1); 
    }
    
    // Calcule la taille fixe en octets nécessaire pour stocker un enregistrement selon les types de colonnes.
    private int calculateRecordSize() {
        int size = 0;
        for (ColumnInfo col : schema) {
            String colType = col.getType();
            if (colType.equals("INT") || colType.equals("FLOAT")) size += 4;
            else if (colType.startsWith("CHAR(")) size += col.getTailleType() * 2;
            else if (colType.startsWith("VARCHAR(")) size += 4 + col.getTailleType() * 2;
        }
        return size;
    }

    // Retourne le nom de la table
    public String getNomRelation() { return nomRelation; }
    
    // Retourne le nombre de colonnes définies
    public int getNumCol() { return numCol; }
    
    // Retourne la liste des objets ColumnInfo
    public ArrayList<ColumnInfo> getSchema() { return schema; }
    
    // Renvoie l'identifiant de la Header Page
    public PageId getHeaderPageId() { return headerPageId; }

    // Méthode qui cherche une page libre, insère le record et déplace la page entre les listes si nécessaire.
    public RecordId InsererRecord(Record record) throws IOException {
        if (tailleRecord == -1) initSchema(); 
        PageId pageId = getIdPageLibre();
        if (pageId == null || pageId.equals(NULL_PAGEID)) {
            addPageDonnees();
            pageId = getIdPageLibre();
        }
        
        RecordId rid = ecrireRecordSurPageDonnees(record, pageId); 
        if (siPagePleine(pageId)) {
        	retirerPageDeListe(pageId, getDebutPagesLibres(), true);
            insererPageDansListe(pageId, getDebutPagesPleines(), false);
        }
        return rid;
    }

    // Donne la liste complète des records
    public ArrayList<Record> GetTousRecords() throws IOException {
        ArrayList<Record> records = new ArrayList<>();
        List<PageId> dataPages = getPagesDonnees();
        for (PageId pageId : dataPages) {
            records.addAll(getRecordsDansPageDonnees(pageId)); 
        }
        return records;
    }
    
    // Supprime un record et recycle la page (suppression si page vide, réutilisation sinon).
    public void SupprimerRecord(RecordId rid) throws IOException {
        PageId pageId = rid.getPageId();
        ByteBuffer buff = bufferManager.GetPage(pageId);
        int slotIdx = rid.getSlotIdx();
        boolean remplieAvantSuppression = siPagePleine(buff);
        
        buff.put(16 + slotIdx, (byte) 0);
        
        boolean isEmpty = siPageVide(buff);
        bufferManager.FreePage(pageId, true);

        if (isEmpty) {
        	retirerPageDeListe(pageId, getDebutPagesLibres(), true); 
            diskManager.DeallocPage(pageId);
        } else if (remplieAvantSuppression) {
        	retirerPageDeListe(pageId, getDebutPagesPleines(), false);
            insererPageDansListe(pageId, getDebutPagesLibres(), true);
        }
    }
    
    // Supprime une table en désallouant toutes ses pages de données et sa page d'en-tête.
    public void supprimer() throws IOException {
        List<PageId> dataPages = getPagesDonnees();
        for (PageId pageId : dataPages) diskManager.DeallocPage(pageId);
        diskManager.DeallocPage(headerPageId);
    }

    // Alloue une nouvelle page et l'ajoute à la liste des pages libres.
    public void addPageDonnees() throws IOException {
        PageId pageId = diskManager.AllocPage();
        ByteBuffer buff = bufferManager.GetPage(pageId);
        buff.putInt(0, -1); buff.putInt(4, -1);
        buff.putInt(8, -1); buff.putInt(12, -1);
        for (int i = 0; i < nombreCases; i++) buff.put(16 + i, (byte) 0);
        bufferManager.FreePage(pageId, true);
        insererPageDansListe(pageId, getDebutPagesLibres(), true);
    }

    // Donne l'identifiant de la première page parmi les pages ayant de l'espace. 
    public PageId getIdPageLibre() throws IOException { return getDebutPagesLibres(); }

    // Donne une liste contenant les identifiants de toutes les pages.
    public List<PageId> getPagesDonnees() throws IOException {
        ArrayList<PageId> pages = new ArrayList<>();
        PageId pageActuelle = getDebutPagesPleines();
        while (!pageActuelle.equals(NULL_PAGEID)) {
            pages.add(pageActuelle);
            pageActuelle = getPageSuivante(pageActuelle);
        }
        pageActuelle = getDebutPagesLibres();
        while (!pageActuelle.equals(NULL_PAGEID)) {
            pages.add(pageActuelle);
            pageActuelle = getPageSuivante(pageActuelle);
        }
        return pages;
    }

    // Écrit le contenu du record dans la première case libre de la page. 
    public RecordId ecrireRecordSurPageDonnees(Record record, PageId pageId) throws IOException {
        ByteBuffer buff = bufferManager.GetPage(pageId);
        int slotIdx = -1;
        for (int i = 0; i < nombreCases; i++) {
            if (buff.get(16 + i) == 0) { slotIdx = i; break; }
        }
        if (slotIdx == -1) {
            bufferManager.FreePage(pageId, false);
            throw new IOException("Page pleine");
        }
        int pos = 16 + nombreCases + (slotIdx * tailleRecord);
        writeRecordToBuffer(record, buff, pos); 
        buff.put(16 + slotIdx, (byte) 1);
        bufferManager.FreePage(pageId, true); 
        return new RecordId(pageId, slotIdx);
    }

    // Donne les enregistements sur une page de données.
    public List<Record> getRecordsDansPageDonnees(PageId pageId) throws IOException {
        ArrayList<Record> records = new ArrayList<>();
        ByteBuffer buff = bufferManager.GetPage(pageId);
        try {
            for (int i = 0; i < nombreCases; i++) {
                if (buff.get(16 + i) == 1) {
                    Record rec = new Record();
                    readFromBuffer(rec, buff, 16 + nombreCases + (i * tailleRecord));
                    records.add(rec);
                }
            }
        } finally { bufferManager.FreePage(pageId, false); }
        return records;
    }
    
    // Écrit les données d'un record à un endroit précis indiqué dans la page.
    public void writeRecordToBuffer(Record record, ByteBuffer buff, int pos) {
        buff.position(pos);
        for (int i = 0; i < numCol; i++) {
            ColumnInfo col = schema.get(i);
            Object val = record.getValeurs().get(i);
            if (col.getType().equals("INT")) buff.putInt((Integer) val);
            else if (col.getType().equals("FLOAT")) buff.putFloat((Float) val);
            else if (col.getType().startsWith("CHAR")) {
                String s = (String) val;
                for (int j = 0; j < col.getTailleType(); j++) buff.putChar(j < s.length() ? s.charAt(j) : '\0');
            } else if (col.getType().startsWith("VARCHAR")) {
                String s = (String) val;
                buff.putInt(s.length());
                for (int j = 0; j < col.getTailleType(); j++) buff.putChar(j < s.length() ? s.charAt(j) : '\0');
            }
        }
    }

    // Lit les informations stockées dans la mémoire pour reconstituer un enregistrement complet.
    public void readFromBuffer(Record record, ByteBuffer buff, int pos) {
        buff.position(pos);
        for (int i = 0; i < numCol; i++) {
            ColumnInfo col = schema.get(i);
            if (col.getType().equals("INT")) record.addValeurs(buff.getInt());
            else if (col.getType().equals("FLOAT")) record.addValeurs(buff.getFloat());
            else if (col.getType().startsWith("CHAR")) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < col.getTailleType(); j++) sb.append(buff.getChar());
                record.addValeurs(sb.toString().trim());
            } else if (col.getType().startsWith("VARCHAR")) {
                int len = buff.getInt();
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < len; j++) sb.append(buff.getChar());
                buff.position(buff.position() + (col.getTailleType() - len) * 2);
                record.addValeurs(sb.toString());
            }
        }
    }

    // Trouve le début de la liste des pages pleines.
    private PageId getDebutPagesPleines() throws IOException { return getDebut(0); }
    
    // Trouve le début de la liste des pages libres.
    private PageId getDebutPagesLibres() throws IOException { return getDebut(8); }
    
    // Lit l'adresse de la première page d'une liste à l'emplacement indiqué dans l'en-tête.
    private PageId getDebut(int offset) throws IOException {
        ByteBuffer buff = bufferManager.GetPage(headerPageId);
        PageId p = new PageId(buff.getInt(offset), buff.getInt(offset + 4));
        bufferManager.FreePage(headerPageId, false);
        return p;
    }
    
    // Indique à partir de quelle page la liste des pages pleines commence.
    private void setDebutListePagesPleines(PageId p) throws IOException { setDebut(0, p); }
    
    // Indique à partir de quelle page la liste des pages libres commence.
    private void setDebutListePagesLibres(PageId p) throws IOException { setDebut(8, p); }
    private void setDebut(int offset, PageId p) throws IOException {
        ByteBuffer buff = bufferManager.GetPage(headerPageId);
        buff.putInt(offset, p.getFileIdx()); buff.putInt(offset+4, p.getPageIdx());
        bufferManager.FreePage(headerPageId, true);
    }

    // Écrit l'identifiant de la page suivante dans l'en-tête d'une page de données.
    private PageId getPageSuivante(PageId pid) throws IOException { return getLien(pid, 8); }
    
    // Écrit l'identifiant de la page précédente dans l'en-tête d'une page de données. 
    private PageId getPagePrecedente(PageId pid) throws IOException { return getLien(pid, 0); }
    
    // Lit l'adresse de la page voisine pour savoir comment l'atteindre.
    private PageId getLien(PageId pid, int offset) throws IOException {
        ByteBuffer buff = bufferManager.GetPage(pid);
        PageId p = new PageId(buff.getInt(offset), buff.getInt(offset + 4));
        bufferManager.FreePage(pid, false);
        return p;
    }
    
    // Écrit l'identifiant de la page suivante dans l'en-tête d'une page de données.
    private void setPageSuivante(PageId pid, PageId next) throws IOException { setLien(pid, 8, next); }
    
    // Écrit l'identifiant de la page précédente dans l'en-tête d'une page de données.
    private void setPagePrecedente(PageId pid, PageId prev) throws IOException { setLien(pid, 0, prev); }
   
    // Note l'adresse des pages voisines sur la page actuelle pour les relier entre elles.
    private void setLien(PageId pid, int offset, PageId target) throws IOException {
        ByteBuffer buff = bufferManager.GetPage(pid);
        buff.putInt(offset, target.getFileIdx()); buff.putInt(offset+4, target.getPageIdx());
        bufferManager.FreePage(pid, true);
    }

    // Place une page au début d'une liste et refait les liens pour qu'elle devienne la nouvelle première page.
    private void insererPageDansListe(PageId pid, PageId head, boolean isFree) throws IOException {
        setPageSuivante(pid, head);
        setPagePrecedente(pid, NULL_PAGEID);
        if (!head.equals(NULL_PAGEID)) setPagePrecedente(head, pid);
        if (isFree) setDebutListePagesLibres(pid); else setDebutListePagesPleines(pid);
    }

    // Retire une page de sa liste en reconnectant ses voisins entre eux.
    private void retirerPageDeListe(PageId pid, PageId head, boolean isFree) throws IOException {
        PageId prev = getPagePrecedente(pid);
        PageId next = getPageSuivante(pid);
        if (!prev.equals(NULL_PAGEID)) setPageSuivante(prev, next);
        if (!next.equals(NULL_PAGEID)) setPagePrecedente(next, prev);
        if (pid.equals(head)) {
            if (isFree) setDebutListePagesLibres(next); else setDebutListePagesPleines(next);
        }
    }
    
    // Vérifie si toutes les cases d'une page sont occupées.
    private boolean siPagePleine(ByteBuffer buff) {
        for (int i = 0; i < nombreCases; i++) if (buff.get(16 + i) == 0) return false;
        return true;
    }
    
    // Méthode vérifiant si toutes les cases d'une page sont occupées.
    private boolean siPagePleine(PageId pid) throws IOException {
        ByteBuffer buff = bufferManager.GetPage(pid);
        boolean full = siPagePleine(buff);
        bufferManager.FreePage(pid, false);
        return full;
    }
    
    // Méthode vérifiant si toutes les cases d'une page sont libres.
    private boolean siPageVide(ByteBuffer buff) {
        for (int i = 0; i < nombreCases; i++) if (buff.get(16 + i) == 1) return false;
        return true;
    }
}