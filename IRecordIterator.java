// Classe permettant de lire un record a la fois 

public interface IRecordIterator {
    
	// Retourne l'enregistrement suivant ou null si tous les enregistrements ont été parcourus.
	Record GetNextRecord() throws Exception;
    
	// Libère les ressources (ferme les fichiers, libère les pages mémoire).
	void Close() throws Exception;
    
	// Réinitialise la lecture pour recommencer le parcours des enregistrements à partir du premier.
	void Reset() throws Exception;
}