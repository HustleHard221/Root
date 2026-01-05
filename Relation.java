// Classe gérant la représentation des tables en stockant les informations de schéma d'une relation.

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.io.IOException;
import java.util.List;

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

    // Constructeur
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
    
    // Ajoute une nouvelle colonne au schéma de la table si il n'est pas encore défini.
    public void addColumn(String colNom, String colType) {
        if (tailleRecord != -1) throw new IllegalStateException("Schéma déja défini");
        this.schema.add(new ColumnInfo(colNom, colType));
        this.numCol++;
    }

    // Calcule la taille d'un enregistrement, détermine le nombre de cases par page et bloque la structure de la table.
    public void initSchema() {
        this.tailleRecord = calculRecordSize();
        int overhead = 16; 
        if (tailleRecord + 1 > config.getPagesize() - overhead) {
            throw new IllegalStateException("Enregistrement trop grand");
        }
        this.nombreCases = (config.getPagesize() - overhead) / (tailleRecord + 1); 
    }
    
    // Calcule la taille totale en octets d'un tuple.
    private int calculRecordSize() {
        int size = 0;
        for (ColumnInfo col : schema) {
            String colType = col.getType();
            if (colType.equals("INT") || colType.equals("FLOAT")) size += 4;
            else if (colType.startsWith("CHAR(")) size += col.getTailleType() * 2;
            else if (colType.startsWith("VARCHAR(")) size += 4 + col.getTailleType() * 2;
        }
        return size;
    }

    // Getters
    public String getNomRelation() { return nomRelation; }
    public int getNumCol() { return numCol; }
    public ArrayList<ColumnInfo> getSchema() { return schema; }
    public PageId getHeaderPageId() { return headerPageId; }
    public int getTailleRecord() { return tailleRecord; }
    public int getNombreCases() { return nombreCases; }

    // Trouve une page libre, y écrit l'enregistrement et met à jour les listes de pages.
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
    
    // Parcourt toutes les pages de données pour récupérer et retourner la liste complète des enregistrements.
    public ArrayList<Record> GetTousRecords() throws IOException {
        ArrayList<Record> records = new ArrayList<>();
        List<PageId> dataPages = getPagesDonnees();
        for (PageId pageId : dataPages) {
            records.addAll(getRecordsDansPageDonnees(pageId)); 
        }
        return records;
    }
    
    // Marque une case comme étant vide et gère le déplacement de la page vers la liste des pages libres.
    public void SupprimerRecord(RecordId rid) throws IOException {
        PageId pageId = rid.getPageId();
        ByteBuffer buff = bufferManager.GetPage(pageId);
        int slotIdx = rid.getSlotIdx();
        boolean remplieAvantSuppression = siPagePleine(buff);
        
        buff.put(16 + slotIdx, (byte) 0);
        
        boolean vide = siPageVide(buff);
        bufferManager.FreePage(pageId, true);

        if (vide) {
        	retirerPageDeListe(pageId, getDebutPagesLibres(), true); 
            diskManager.DeallocPage(pageId);
        } else if (remplieAvantSuppression) {
        	retirerPageDeListe(pageId, getDebutPagesPleines(), false);
            insererPageDansListe(pageId, getDebutPagesLibres(), true);
        }
    }
    
    // Modifie directement dans le buffer la valeur d'une colonne spécifique pour un enregistrement donné.
    public void UpdateRecord(RecordId rid, int colIndex, Object nvelleValeur) throws IOException {
        PageId pageId = rid.getPageId();
        ByteBuffer buff = bufferManager.GetPage(pageId);
        try {
            int slotIdx = rid.getSlotIdx();
            int pos = 16 + nombreCases + (slotIdx * tailleRecord);
            
            for(int i=0; i<colIndex; i++) {
                ColumnInfo col = schema.get(i);
                if(col.getType().equals("INT") || col.getType().equals("FLOAT")) pos += 4;
                else if(col.getType().startsWith("CHAR")) pos += col.getTailleType() * 2;
                else if(col.getType().startsWith("VARCHAR")) pos += 4 + (col.getTailleType() * 2);
            }
            
            ColumnInfo col = schema.get(colIndex);
            buff.position(pos);
            
            if (col.getType().equals("INT")) buff.putInt((Integer) nvelleValeur);
            else if (col.getType().equals("FLOAT")) buff.putFloat((Float) nvelleValeur);
            else if (col.getType().startsWith("CHAR")) {
                String s = (String) nvelleValeur;
                for (int j = 0; j < col.getTailleType(); j++) buff.putChar(j < s.length() ? s.charAt(j) : '\0');
            } else if (col.getType().startsWith("VARCHAR")) {
                String s = (String) nvelleValeur;
                buff.putInt(s.length());
                for (int j = 0; j < col.getTailleType(); j++) buff.putChar(j < s.length() ? s.charAt(j) : '\0');
            }
        } finally {
            bufferManager.FreePage(pageId, true);
        }
    }

    // Supprime la table
    public void supprimer() throws IOException {
        List<PageId> dataPages = getPagesDonnees();
        for (PageId pageId : dataPages) diskManager.DeallocPage(pageId);
        diskManager.DeallocPage(headerPageId);
    }

    // Alloue une nouvelle page, l'initialise et l'ajoute à la liste des pages libres.
    public void addPageDonnees() throws IOException {
        PageId pageId = diskManager.AllocPage();
        ByteBuffer buff = bufferManager.GetPage(pageId);
        buff.putInt(0, -1); buff.putInt(4, -1);
        buff.putInt(8, -1); buff.putInt(12, -1);
        for (int i = 0; i < nombreCases; i++) buff.put(16 + i, (byte) 0);
        bufferManager.FreePage(pageId, true);
        insererPageDansListe(pageId, getDebutPagesLibres(), true);
    }

    // Retourne l'identifiant de la première page disponible dans la liste des pages libres.
    public PageId getIdPageLibre() throws IOException { return getDebutPagesLibres(); }

    // Construit et retourne la liste de tous les identifiants de pages appartenant à la table.
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

    // Cherche la première case vide sur une page et y écrit les données d'un enregistrement.
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

    
    // Lit une page et reconstruit une liste d'objets Record pour chaque case occupée.
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
    
    // Écrit le tuple dans le buffer à la position donnée.
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

    // Lit les donées du buffer à une position donnée pour remplir les valeurs d'un objet Record.
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

    // Lit dans l'entête l'identifiant de la première page de la liste des pages pleines.
    private PageId getDebutPagesPleines() throws IOException { return getDebut(0); }
    
    // Lit dans l'entête l'identifiant de la première page de la liste des pages libres.
    private PageId getDebutPagesLibres() throws IOException { return getDebut(8); }
    
    // Méthode pour lire un PageId au début de l'entête.
    private PageId getDebut(int offset) throws IOException {
        ByteBuffer buff = bufferManager.GetPage(headerPageId);
        PageId p = new PageId(buff.getInt(offset), buff.getInt(offset + 4));
        bufferManager.FreePage(headerPageId, false);
        return p;
    }
    
    // Met à jour le pointeur de début de la liste des pages pleines dans l'entête.
    private void setDebutListePagesPleines(PageId p) throws IOException { setDebut(0, p); }
    private void setDebutListePagesLibres(PageId p) throws IOException { setDebut(8, p); }
    
    // Met à jour le pointeur de début de la liste des pages libres dans l'entête.
    private void setDebut(int offset, PageId p) throws IOException {
        ByteBuffer buff = bufferManager.GetPage(headerPageId);
        buff.putInt(offset, p.getFileIdx()); buff.putInt(offset+4, p.getPageIdx());
        bufferManager.FreePage(headerPageId, true);
    }

    // Récupère l'identifiant de la page suivante liée à une page.
    private PageId getPageSuivante(PageId pid) throws IOException { return getLien(pid, 8); }
   
    // Récupère l'identifiant de la page précédente liée à une page.
    private PageId getPagePrecedente(PageId pid) throws IOException { return getLien(pid, 0); }
    
    // Lit les pointeurs stockés au début d'une page de données. 
    private PageId getLien(PageId pid, int offset) throws IOException {
        ByteBuffer buff = bufferManager.GetPage(pid);
        PageId p = new PageId(buff.getInt(offset), buff.getInt(offset + 4));
        bufferManager.FreePage(pid, false);
        return p;
    }
    
    // Écrit le lien vers la page suivante dans l'entête de la page actuelle.
    private void setPageSuivante(PageId pid, PageId next) throws IOException { setLien(pid, 8, next); }
    
    // Écrit le lien vers la page précédente dans l'en-tête de la page actuelle.
    private void setPagePrecedente(PageId pid, PageId prev) throws IOException { setLien(pid, 0, prev); }
   
    // Enregistre l'adresse de la page (suivante ou précédente) à la position indiquée dans le fichier.
    private void setLien(PageId pid, int offset, PageId target) throws IOException {
        ByteBuffer buff = bufferManager.GetPage(pid);
        buff.putInt(offset, target.getFileIdx()); buff.putInt(offset+4, target.getPageIdx());
        bufferManager.FreePage(pid, true);
    }
    
    // Insère une page en tête de liste des pages pleines ou libres.
    private void insererPageDansListe(PageId pid, PageId head, boolean isFree) throws IOException {
        setPageSuivante(pid, head);
        setPagePrecedente(pid, NULL_PAGEID);
        if (!head.equals(NULL_PAGEID)) setPagePrecedente(head, pid);
        if (isFree) setDebutListePagesLibres(pid); else setDebutListePagesPleines(pid);
    }

    // Détache une page de sa liste en mettant à jour les liens des pages voisines.
    private void retirerPageDeListe(PageId pid, PageId head, boolean isFree) throws IOException {
        PageId prev = getPagePrecedente(pid);
        PageId next = getPageSuivante(pid);
        if (!prev.equals(NULL_PAGEID)) setPageSuivante(prev, next);
        if (!next.equals(NULL_PAGEID)) setPagePrecedente(next, prev);
        if (pid.equals(head)) {
            if (isFree) setDebutListePagesLibres(next); else setDebutListePagesPleines(next);
        }
    }
    
    // Vérifie si toutes les cases d'une page chargée en mémoire sont occupés.
    private boolean siPagePleine(ByteBuffer buff) {
        for (int i = 0; i < nombreCases; i++) if (buff.get(16 + i) == 0) return false;
        return true;
    }
    
    // Vérifie si une page est remplie.
    private boolean siPagePleine(PageId pid) throws IOException {
        ByteBuffer buff = bufferManager.GetPage(pid);
        boolean full = siPagePleine(buff);
        bufferManager.FreePage(pid, false);
        return full;
    }
    
    // Vérifie si toutes les cases d'une page sont vides.
    private boolean siPageVide(ByteBuffer buff) {
        for (int i = 0; i < nombreCases; i++) if (buff.get(16 + i) == 1) return false;
        return true;
    }
}