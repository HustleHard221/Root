// Point d'entrée et classe principale du SGBD. Elle joue le rôle d'interface entre l'utilisateur et les classes internes.

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.StringTokenizer;

public class SGBD {
    private DBConfig config;
    private DiskManager diskManager;
    private BufferManager bufferManager;
    private DBManager dbManager;
    
    // Constructeur
    public SGBD(DBConfig config) {
        this.config = config;
        this.diskManager = new DiskManager(config);
        this.bufferManager = new BufferManager(config, diskManager);
        this.dbManager = new DBManager(config, diskManager, bufferManager);
    }
    
    // Démarre la boucle principale qui attend les commandes de l'utilisateur.
    public void Run() {
        try { 
            diskManager.Init(); 
            dbManager.LoadState(); 
        } catch (IOException e) { 
            e.printStackTrace(); 
        }
        
        Scanner sc = new Scanner(System.in);
        System.out.println("SGBD démarré");
        boolean running = true;
        
        while (running && sc.hasNextLine()) {
            String cmd = sc.nextLine().trim();
            if (cmd.isEmpty()) continue;
            
            try {
                if (cmd.equals("EXIT")) { 
                    try { 
                        dbManager.SaveState(); 
                        bufferManager.FlushBuffers(); 
                        diskManager.Finish(); 
                    } catch (Exception e) {}
                    System.out.println("Au revoir"); 
                    running = false; 
                }
                else if (cmd.equals("DESCRIBE TABLES")) dbManager.DescribeAllTables();
                else if (cmd.equals("DROP TABLES")) { 
                    try { 
                        dbManager.RemoveAllTables(); 
                        System.out.println("Tables supprimées"); 
                    } catch(Exception e){} 
                }
                else if (cmd.startsWith("CREATE TABLE ")) ProcessCreateTable(cmd);
                else if (cmd.startsWith("DROP TABLE ")) ProcessDropTable(cmd);
                else if (cmd.startsWith("DESCRIBE TABLE ")) dbManager.DescribeTable(cmd.split("\\s+")[2]);
                else if (cmd.startsWith("INSERT INTO ")) ProcessInsert(cmd);
                else if (cmd.startsWith("APPEND INTO ")) importation(cmd);
                else if (cmd.startsWith("SELECT ")) ProcessSelection(cmd);
                else if (cmd.startsWith("DELETE ")) ProcessDelete(cmd);
                else if (cmd.startsWith("UPDATE ")) ProcessMaj(cmd);
                else System.out.println("Commande inconnue");
            } catch (Exception e) {
                System.out.println("Erreur : " + e.getMessage());
            }
        }
        sc.close();
    }

    // Crée une nouvelle page en allouant une page d'entête
    private void ProcessCreateTable(String cmd) {
        try {
            String sub = cmd.substring(0, cmd.indexOf('(')).trim();
            String name = sub.split("\\s+")[2];
            PageId h = diskManager.AllocPage();
            ByteBuffer b = bufferManager.GetPage(h);
            b.putInt(0,-1); b.putInt(4,-1); b.putInt(8,-1); b.putInt(12,-1);
            bufferManager.FreePage(h, true);
            
            Relation r = new Relation(name, config, diskManager, bufferManager, h);
            
            String[] cols = cmd.substring(cmd.indexOf('(')+1, cmd.lastIndexOf(')')).split(",");
            for (String c : cols) { 
                String[] p = c.trim().split(":"); 
                r.addColumn(p[0].trim(), p[1].trim()); 
            }
            r.initSchema();
            dbManager.AddTable(r);
            System.out.println("Table " + name + " created");
        } catch (Exception e) { System.out.println("Erreur : " + e.getMessage()); }
    }

    // Supprime une table de la mémoire et du disque via le gestionnaire de base de données.
    private void ProcessDropTable(String cmd) {
        try { 
            dbManager.RemoveTable(cmd.split("\\s+")[2]); 
            System.out.println("Table supprimée"); 
        } catch (Exception e) {}
    }

    // Analyse une commande INSERT INTO pour créer un enregistrement à partir des valeurs fournies.
    private void ProcessInsert(String cmd) throws Exception {
        String[] parts = cmd.split(" VALUES ");
        if (parts.length != 2) throw new Exception("Erreur de syntaxe");
        
        String relName = parts[0].substring(12).trim();
        String valStr = parts[1].trim();
        valStr = valStr.substring(1, valStr.length()-1);
        
        Relation r = dbManager.GetTable(relName);
        if (r == null) throw new Exception("Table inconnue");
        
        Record rec = ParserRecord(valStr, r);
        r.InsererRecord(rec);
        System.out.println("1 enregistrement inséré");
    }
    
    // Convertit une chaîne de caractères brute en un objet Record.
    private Record ParserRecord(String line, Relation r) throws Exception {
        String[] tokens = line.split(",");
        if (tokens.length != r.getNumCol()) throw new Exception("Nombre de colonnes incorrect");
        
        Record rec = new Record();
        for (int i=0; i<r.getNumCol(); i++) {
            ColumnInfo col = r.getSchema().get(i);
            String token = tokens[i].trim();
            
            if (col.getType().equals("INT")) rec.addValeurs(Integer.parseInt(token));
            else if (col.getType().equals("FLOAT")) rec.addValeurs(Float.parseFloat(token));
            else {
                 if (token.startsWith("\"") && token.endsWith("\"")) 
                     token = token.substring(1, token.length()-1);
                 rec.addValeurs(token);
            }
        }
        return rec;
    }

    // Lit un fichier externe ligne par ligne pour insérer massivement tous ses enregistrements dans une table.
    private void importation(String cmd) throws Exception {
        String[] parts = cmd.split(" ALLRECORDS ");
        if (parts.length != 2) throw new Exception("Erreur de syntaxe");
        
        String nomRelation = parts[0].substring(12).trim();
        String partieFichier = parts[1].trim();
        String nomFichier = partieFichier.substring(1, partieFichier.length()-1);
        
        Relation r = dbManager.GetTable(nomRelation);
        if (r == null) throw new Exception("Table inconnue");
        
        int count = 0;
        
        try (BufferedReader br = new BufferedReader(new FileReader(config.getDbpath() + "/" + nomFichier))) { 
            String line;
            while ((line = br.readLine()) != null) {
                try {
                    Record rec = ParserRecord(line, r);
                    r.InsererRecord(rec);
                    count++;
                } catch(Exception e) { System.out.println("Erreur lors de l'importation de la ligne: " + line); }
            }
        }
        System.out.println("Enregistrements insérés: " + count);
    }
    
    // Exécute une requête SELECT en construisant une chaîne d'itérateurs.
    private void ProcessSelection(String cmd) throws Exception {
        
        String[] divisionFrom = cmd.split(" FROM ");
        if (divisionFrom.length != 2) throw new Exception("Erreur de syntaxe : mot-clé FROM manquant");
        
        String partieSelection = divisionFrom[0].substring(7).trim();
        String reste = divisionFrom[1];
        
        String[] divisionWhere = reste.split(" WHERE ");
        String partieTable = divisionWhere[0].trim();
        String partieConditions = (divisionWhere.length > 1) ? divisionWhere[1].trim() : null;
        
        String[] tableInfo = partieTable.split("\\s+");
        String nomRelation = tableInfo[0];
        String alias = (tableInfo.length > 1) ? tableInfo[1] : "";
        
        Relation r = dbManager.GetTable(nomRelation);
        if (r == null) throw new Exception("Table non trouvée: " + nomRelation);
        
        RelationScanner scanner = new RelationScanner(r, bufferManager);
        IRecordIterator iter = scanner;
        
        if (partieConditions != null) {
            List<Condition> conditions = ParseConditions(partieConditions, r, alias);
            iter = new SelectOperator(iter, conditions);
        }
        
        List<Integer> indicesProjection = new ArrayList<>();
        if (!partieSelection.equals("*")) {
            String[] cols = partieSelection.split(",");
            for (String c : cols) {
                c = c.trim();
                
                if (c.contains(".")) c = c.split("\\.")[1];
                
                int idx = -1;
                for (int i=0; i<r.getNumCol(); i++) {
                    if (r.getSchema().get(i).getNom().equals(c)) { idx = i; break; }
                }
                if (idx == -1) throw new Exception("Colonne " + c + " non trouvée");
                indicesProjection.add(idx);
            }
            iter = new ProjectOperator(iter, indicesProjection);
        }
        
        RecordPrinter printer = new RecordPrinter(iter);
        printer.Print();
    }
    
    // Parcourt une table pour identifier les enregistrements correspondant à une condition WHERE et les supprime.
    private void ProcessDelete(String cmd) throws Exception {
        String[] divisionWhere = cmd.split(" WHERE ");
        String partieTable = divisionWhere[0].substring(7).trim();
        String partieConditions = (divisionWhere.length > 1) ? divisionWhere[1].trim() : null;
        
        String[] tableInfo = partieTable.split("\\s+");
        String relName = tableInfo[0];
        String alias = (tableInfo.length > 1) ? tableInfo[1] : "";
        
        Relation r = dbManager.GetTable(relName);
        if (r == null) throw new Exception("Table inconnue");
        
        List<Condition> conds = null;
        if (partieConditions != null) conds = ParseConditions(partieConditions, r, alias);
        
        RelationScanner scanner = new RelationScanner(r, bufferManager);
        int count = 0;
        
        List<RecordId> recordsASupprimer = new ArrayList<>();
        
        Record rec;
        while ((rec = scanner.GetNextRecord()) != null) {
            boolean correspond = true;
            if (conds != null) {
                for (Condition c : conds) if (!c.verifier(rec)) { correspond = false; break; }
            }
            if (correspond) recordsASupprimer.add(scanner.getLastRecordId());
        }
        scanner.Close();
        
        for (RecordId rid : recordsASupprimer) {
            r.SupprimerRecord(rid);
            count++;
        }
        System.out.println("Nombre total d'enregistrements supprimés = " + count);
    }
    
    // Identifie les lignes à modifier selon une condition WHERE et met à jour leurs colonnes avec les nouvelles valeurs définies dans le SET.
    private void ProcessMaj(String cmd) throws Exception {
        String[] partiesSet = cmd.split(" SET ");
        if (partiesSet.length != 2) throw new Exception("Erreur de syntaxe : mot-clé SET manquant");
        
        String partieTable = partiesSet[0].substring(7).trim();
        String reste = partiesSet[1];
        
        String[] partiesWhere = reste.split(" WHERE ");
        String partieSet = partiesWhere[0].trim();
        String partieConditions = (partiesWhere.length > 1) ? partiesWhere[1].trim() : null;
        
        String[] tableInfo = partieTable.split("\\s+");
        String nomRelation = tableInfo[0];
        String alias = (tableInfo.length > 1) ? tableInfo[1] : "";
        
        Relation r = dbManager.GetTable(nomRelation);
        if (r == null) throw new Exception("Table inconnue");
        
        String[] maj = partieSet.split(",");
        List<Integer> majColIndex = new ArrayList<>();
        List<Object> majValeurs = new ArrayList<>();
        
        for (String affectation : maj) {
            String[] elemAffectation = affectation.split("=");
            if (elemAffectation.length != 2) throw new Exception("Erreur de syntaxe dans la clause SET");
            
            String nomCol = elemAffectation[0].trim();
            if (nomCol.contains(".")) nomCol = nomCol.split("\\.")[1];
            
            int index = -1;
            for (int i=0; i<r.getNumCol(); i++) if (r.getSchema().get(i).getNom().equals(nomCol)) index = i;
            if (index == -1) throw new Exception("Colonne " + nomCol + " inconnue");
            
            String chaineValeur = elemAffectation[1].trim();
            ColumnInfo ci = r.getSchema().get(index);
            Object valeur = null;
            
            if (ci.getType().equals("INT")) valeur = Integer.parseInt(chaineValeur);
            else if (ci.getType().equals("FLOAT")) valeur = Float.parseFloat(chaineValeur);
            else {
                 if (chaineValeur.startsWith("\"")) chaineValeur = chaineValeur.substring(1, chaineValeur.length()-1);
                 valeur = chaineValeur;
            }
            majColIndex.add(index);
            majValeurs.add(valeur);
        }
        
        List<Condition> conditions = null;
        if (partieConditions != null) conditions = ParseConditions(partieConditions, r, alias);
        
        RelationScanner scanner = new RelationScanner(r, bufferManager);
        int count = 0;
        
        Record rec;
        while ((rec = scanner.GetNextRecord()) != null) {
            boolean valide = true;
            if (conditions != null) {
                for (Condition c : conditions) if (!c.verifier(rec)) { valide = false; break; }
            }
            if (valide) {
                
                for (int i=0; i<majColIndex.size(); i++) {
                    r.UpdateRecord(scanner.getLastRecordId(), majColIndex.get(i), majValeurs.get(i));
                }
                count++;
            }
        }
        scanner.Close();
        System.out.println("Nombre total d'enregistrements mis à jour = " + count);
    }
    
    // Convertit le texte du WHERE en une liste d'objets Condition exploitables.
    private List<Condition> ParseConditions(String wherePart, Relation r, String alias) throws Exception {
        List<Condition> list = new ArrayList<>();
        String[] conditions = wherePart.split(" AND ");
        for (String c : conditions) {
            
            String operateur = "";
            if (c.contains("<=")) operateur = "<=";
            else if (c.contains(">=")) operateur = ">=";
            else if (c.contains("<>")) operateur = "<>";
            else if (c.contains("=")) operateur = "=";
            else if (c.contains("<")) operateur = "<";
            else if (c.contains(">")) operateur = ">";
            
            if (operateur.isEmpty()) continue;
            
            String[] termes = c.split(operateur);
            String gauche = termes[0].trim();
            String droite = termes[1].trim();
            
            if (gauche.contains(".")) gauche = gauche.split("\\.")[1];
            int colIndex = -1;
            for (int i=0; i<r.getNumCol(); i++) if (r.getSchema().get(i).getNom().equals(gauche)) colIndex = i;
            if (colIndex == -1) throw new Exception("Colonne inconnue dans la partie WHERE: " + gauche);
            
            int colIndex2 = -1;
            String partieDroite = droite;
            if (partieDroite.contains(".")) partieDroite = partieDroite.split("\\.")[1];
            
            for (int i=0; i<r.getNumCol(); i++) if (r.getSchema().get(i).getNom().equals(partieDroite)) colIndex2 = i;
            
            if (colIndex2 != -1) {
                list.add(new Condition(colIndex, operateur, colIndex2));
            } else {
                Object valeurConstante = null;
                ColumnInfo ci = r.getSchema().get(colIndex);
                if (ci.getType().equals("INT")) valeurConstante = Integer.parseInt(droite);
                else if (ci.getType().equals("FLOAT")) valeurConstante = Float.parseFloat(droite);
                else {
                    if (droite.startsWith("\"")) droite = droite.substring(1, droite.length()-1);
                    valeurConstante = droite;
                }
                list.add(new Condition(colIndex, operateur, valeurConstante));
            }
        }
        return list;
    }

    // Méthode main permettant de charger le fichier de configuration et de lancer l'instance du SGBD.
    public static void main(String[] args) {
        if (args.length != 1) { 
            System.out.println("Utilisation : java SGBD <config_file>"); 
            return; 
        }
        try { 
            new SGBD(DBConfig.LoadDBConfig(args[0])).Run(); 
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }
}