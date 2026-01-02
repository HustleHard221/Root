// Classe filtrant les enregistrements en renvoyant seulement ceux respectant les conditions demandées.

import java.util.List;

public class SelectOperator implements IRecordIterator {
    private IRecordIterator source;
    private List<Condition> conditions;

    // Constructeur
    public SelectOperator(IRecordIterator child, List<Condition> conditions) {
        this.source = child;
        this.conditions = conditions;
    }

    // Méthode parcourant les enregistrements de la source un par un pour renvoyer le prochain qui valide toutes les conditions.
    @Override
    public Record GetNextRecord() throws Exception {
        Record r;
        while ((r = source.GetNextRecord()) != null) {
            boolean correspondance = true;
            if (conditions != null) {
                for (Condition c : conditions) {
                    if (!c.verifier(r)) {
                        correspondance = false;
                        break;
                    }
                }
            }
            if (correspondance) return r;
        }
        return null;
    }

    // Ferme l'itérateur source pour libérer les ressources.
    @Override
    public void Close() throws Exception { source.Close(); }

    // Relance la lecture de la source depuis le début.
    @Override
    public void Reset() throws Exception { source.Reset(); }
}