public class ColumnInfo {
    private String nom;
    private String type;
    
    // Constructeur
    public ColumnInfo(String name, String type) {
        this.nom = name;
        this.type = type;
    }

    // Retourne le nom de la colonne comme défini dans la création de la table.
    public String getNom() { return nom; }
    
    // Retourne le type de la colonne (INT, FLOAT, CHAR(), VARCHAR())
    public String getType() { return type; }

    // Retourne la longueur maximale pour les types.
    public int getTailleType() {
        if (type.startsWith("CHAR(") || type.startsWith("VARCHAR(")) {
            try {
                return Integer.parseInt(type.substring(type.indexOf('(') + 1, type.indexOf(')')));
            } catch (Exception e) {
                throw new IllegalArgumentException("Type inconnu ou format invalide : " + type);
            }
        }
        return 0;
    }
}