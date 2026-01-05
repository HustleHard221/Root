// Classe filtrant les colonnes : elle prend chaque ligne complète et renvoie seulement les informations demandées.

import java.util.List;

// Constructeur
public class ProjectOperator implements IRecordIterator {
    private IRecordIterator sourceDonnees;
    private List<Integer> colIndices;

    public ProjectOperator(IRecordIterator sourceDonnees, List<Integer> colIndices) {
        this.sourceDonnees = sourceDonnees;
        this.colIndices = colIndices;
    }

    @Override
    // Lit l'enregistrement suivant et renvoie uniquement les colonnes choisies.
    public Record GetNextRecord() throws Exception {
        Record r = sourceDonnees.GetNextRecord();
        if (r == null) return null;
       
        if (colIndices == null || colIndices.isEmpty()) return r;

        Record newRec = new Record();
        for (int idx : colIndices) {
            newRec.addValeurs(r.getValeurs().get(idx));
        }
        return newRec;
    }

    @Override
    // Méthode déclenchant l'arret de la lecture et la fermeture des fichiers.
    public void Close() throws Exception { sourceDonnees.Close(); }

    @Override
    // Recommence la lecture de la source à partir du début.
    public void Reset() throws Exception { sourceDonnees.Reset(); }
}