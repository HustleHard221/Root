// Cette classe modélise les comparaisons et vérifie si les enregistrements respectent le critère de filtrage.

public class Condition {
    private int colIndex;
    private String operator;
    private Object valeurConstante;
    private int colIndex2;
    private boolean comparaisonColonnes;

    // Constructeur qui gère les comparaisons entre une colonne et une valeur constante.
    public Condition(int colIndex, String operator, Object val) {
        this.colIndex = colIndex;
        this.operator = operator;
        this.valeurConstante = val;
        this.comparaisonColonnes = false;
    }
    
    // Constructeur qui gère les comparaisons entre 2 colonnes.
    public Condition(int colIndex, String operator, int colIndex2) {
        this.colIndex = colIndex;
        this.operator = operator;
        this.colIndex2 = colIndex2;
        this.comparaisonColonnes = true;
    }
    
    // Retourne true si la comparaison est vérifiée, et false sinon.
    public boolean verifier(Record record) {
        Object v1 = record.getValeurs().get(colIndex);
        Object v2 = comparaisonColonnes ? record.getValeurs().get(colIndex2) : valeurConstante;

        
        if (v1 instanceof Integer) {
            int i1 = (Integer) v1;
            int i2 = (Integer) v2;
            switch (operator) {
                case "=": return i1 == i2;
                case "<": return i1 < i2;
                case ">": return i1 > i2;
                case "<=": return i1 <= i2;
                case ">=": return i1 >= i2;
                case "<>": return i1 != i2;
            }
        } else if (v1 instanceof Float) {
            float f1 = (Float) v1;
            float f2 = (v2 instanceof Integer) ? ((Integer)v2).floatValue() : (Float) v2;
            switch (operator) {
                case "=": return f1 == f2;
                case "<": return f1 < f2;
                case ">": return f1 > f2;
                case "<=": return f1 <= f2;
                case ">=": return f1 >= f2;
                case "<>": return f1 != f2;
            }
        } else if (v1 instanceof String) {
            String s1 = (String) v1;
            String s2 = (String) v2;
            int comparaison = s1.compareTo(s2);
            switch (operator) {
                case "=": return comparaison == 0;
                case "<": return comparaison < 0;
                case ">": return comparaison > 0;
                case "<=": return comparaison <= 0;
                case ">=": return comparaison >= 0;
                case "<>": return comparaison != 0;
            }
        }
        return false;
    }
}