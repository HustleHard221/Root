import java.io.BufferedReader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
public class DBConfig {
	
	private String dbpath;
	private int pagesize;
	private int dm_maxfilecount;
	
	public DBConfig(String dbpath, int pagesize, int dm_maxfilecount) {
		this.dbpath=dbpath;
		this.pagesize=pagesize;
		this.dm_maxfilecount=dm_maxfilecount;
	}
	
	public static DBConfig LoadDBConfig(String fichier_config) {
		String dbpath = null;
        int pagesize = 0;
        int dm_maxfilecount = 0;
		try(BufferedReader reader = new BufferedReader(new FileReader(fichier_config))) {
			String ligne;
			while ((ligne = reader.readLine()) != null) { // Lire chaque ligne du fichier
				ligne = ligne.trim();
				if (ligne.isEmpty() || ligne.startsWith("#")) {
					continue; // Ignorer les lignes vides et les commentaires
				}
				String[] parts = ligne.split("=",2); // Diviser la ligne en clé et valeur
				if (parts.length == 2) {
					String clé = parts[0].trim();
					String valeur = parts[1].trim();
					switch (clé) {
						case "dbpath":
							dbpath = valeur;
							break;
						case "pagesize":
							pagesize = Integer.parseInt(valeur);
							break;
						case "dm_maxfilecount":
							dm_maxfilecount = Integer.parseInt(valeur);
							break;
					}
				}
			}
		}
		catch (IOException e) {
			System.out.println("Erreur de lecture du fichier de configuration : " + e.getMessage());
		
		}		
		return new DBConfig(dbpath, pagesize, dm_maxfilecount);//
	}
}