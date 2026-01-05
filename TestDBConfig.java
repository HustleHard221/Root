// Tests unitaires de la classe DBConfig

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class TestDBConfig {
    
	// Lance les tests unitaires pour le constructeur et le chargement de configuration.
	public static void main(String[] args) {
        testConstructeur();
        testChargementConfig();
    }
    
	// Vérifie qu'il est possible d'instancier manuellement un objet DBConfig.
	private static void testConstructeur() {
        new DBConfig("DB", 4096, 10, 10, "LRU");
        System.out.println("Initialisation du constructeur DBConfig réussie");
    }
    
	// Crée un fichier temporaire test.cfg et teste si la méthode LoadDBConfig parvient à lire et charger correctement les paramètres depuis ce fichier.
	private static void testChargementConfig() {
        try (PrintWriter pw = new PrintWriter(new FileWriter("test.cfg"))) {
            pw.println("dbpath=DB\npagesize=4096\ndm_maxfilecount=5\nbm_buffercount=5\nbm_policy=LRU");
        } catch (IOException e) {}
        try { DBConfig.LoadDBConfig("test.cfg"); System.out.println("Test du chargement de la config réussi"); } catch (Exception e) { System.out.println("Échec"); }
    }
}