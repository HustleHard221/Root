import java.util.ArrayList;
import java.util.Objects;

// Classe gérant les enregistrements

public class Record {

	// Liste valeurs stockées dans l'enregistrement
	private ArrayList<Object> valeurs;

	
    public Record() { this.valeurs = new ArrayList<>(); }

    public void addValeurs(Object val) { this.valeurs.add(val); }
    public ArrayList<Object> getValeurs() { return this.valeurs; }

    @Override
    // Affiche le contenu du record
    public String toString() { return "Record" + valeurs.toString(); }

    @Override
    // Compare 2 deux records pour vérifier si les informations sont les mêmes
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Record record = (Record) obj;
        return Objects.equals(valeurs, record.valeurs);
    }

    @Override
    // Génère une signature unique pour chaque enregistrement
    public int hashCode() { return Objects.hash(valeurs); }
}