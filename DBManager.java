// Gère le catalogue des tables (ajout, suppression, accès) et les changements d'état de la base de données (sauvegarde et chargement de la liste des tables et de leurs schémas).

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DBManager {
    private DBConfig config;
    private DiskManager diskManager;
    private BufferManager bufferManager;
    private Map<String, Relation> tables;

    // Constructeur
    public DBManager(DBConfig config, DiskManager dm, BufferManager bm) {
        this.config = config;
        this.diskManager = dm;
        this.bufferManager = bm;
        this.tables = new HashMap<>();
    }

    // Ajoute un objet Relation à la mémoire du gestionnaire.
    public void AddTable(Relation tab) {
        if (tables.containsKey(tab.getNomRelation())) throw new IllegalArgumentException("Table existante");
        tables.put(tab.getNomRelation(), tab);
    }
    
    // Recherche et retourne un objet Relation correspondant à un nom donné.
    public Relation GetTable(String nom) { return tables.get(nom); }

    // Supprime une table de la mémoire.
    public void RemoveTable(String nom) throws IOException {
        Relation rel = tables.get(nom);
        if (rel != null) {
            rel.supprimer();
            tables.remove(nom);
        }
    }

    // Vide la base de données en supprimant toutes les tables enregistrées.
    public void RemoveAllTables() throws IOException {
        for (String name : new ArrayList<>(tables.keySet())) RemoveTable(name);
    }

    // Affiche dans la console le nom de la table et la liste de ses colonnes ainsi que leurs types.
    public void DescribeTable(String nom) {
        Relation rel = tables.get(nom);
        if (rel == null) { System.out.println("Table introuvable."); return; }
        StringBuilder sb = new StringBuilder(rel.getNomRelation()).append(" (");
        for (int i = 0; i < rel.getSchema().size(); i++) {
            ColumnInfo col = rel.getSchema().get(i);
            sb.append(col.getNom()).append(":").append(col.getType());
            if (i < rel.getSchema().size() - 1) sb.append(", ");
        }
        System.out.println(sb.append(")"));
    }

    // Affiche le schéma de toutes les tables présentes dans la base.
    public void DescribeAllTables() {
        if (tables.isEmpty()) System.out.println("Aucune table.");
        else for (String name : tables.keySet()) DescribeTable(name);
    }

    // Sauvegarde la liste des tables et leurs schémas dans le fichier database.save
    public void SaveState() throws IOException {
        File f = new File(config.getDbpath(), "database.save");
        try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
            for (Relation r : tables.values()) {
                PageId h = r.getHeaderPageId();
                pw.println(r.getNomRelation() + " " + h.getFileIdx() + " " + h.getPageIdx() + " " + r.getNumCol());
                for (ColumnInfo c : r.getSchema()) pw.println(c.getNom() + " " + c.getType());
            }
        }
    }

    // Lit le fichier database.save pour restaurer l'état de la base de données.
    public void LoadState() throws IOException {
        File f = new File(config.getDbpath(), "database.save");
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim(); if (line.isEmpty()) continue;
                String[] p = line.split(" ");
                Relation r = new Relation(p[0], config, diskManager, bufferManager, new PageId(Integer.parseInt(p[1]), Integer.parseInt(p[2])));
                int nbCols = Integer.parseInt(p[3]);
                for (int i = 0; i < nbCols; i++) {
                    String[] c = br.readLine().split(" ");
                    r.addColumn(c[0], c[1]);
                }
                r.initSchema();
                tables.put(r.getNomRelation(), r);
            }
        }
    }
}