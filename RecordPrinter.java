// Cette classe affiche les résultats finaux d'une requête sur la console.

import java.util.ArrayList;

public class RecordPrinter {
    private IRecordIterator iterator;

    // Constructeur
    public RecordPrinter(IRecordIterator iterator) {
        this.iterator = iterator;
    }

    // Lance la boucle qui récupère tous les enregistrements.
    public void Print() throws Exception {
        int count = 0;
        Record rec;
        
        while ((rec = iterator.GetNextRecord()) != null) {
            StringBuilder sb = new StringBuilder();
            ArrayList<Object> valeurs = rec.getValeurs();
            
            for (int i = 0; i < valeurs.size(); i++) {
                sb.append(valeurs.get(i));
                if (i < valeurs.size() - 1) {
                    sb.append(" ; ");
                }
            }
            
            System.out.println(sb.toString());
            count++;
        }
        
        System.out.println("Enregistrements sélectionnés = " + count);
        
        iterator.Close(); 
    }
}