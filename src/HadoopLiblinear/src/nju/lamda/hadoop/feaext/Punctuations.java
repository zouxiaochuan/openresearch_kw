package nju.lamda.hadoop.feaext;

import java.util.Arrays;
import java.util.HashSet;

public class Punctuations {
	
	public static boolean IsPunctuation(String str)
	{
		return puunctuations.contains(str);
	}
	public static final HashSet<String> puunctuations
	= new HashSet<String>(Arrays.asList(
			",",
			"?",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"~",
			"!",
			".",
			":",
			"\"",
			"'",
			"(",
			")",
			"*",
			"A",
			"--",
			"..",
			">>",
			" [",
			" ]",
			"",
			"<",
			">",
			"/",
			"\\",
			"|",
			"-",
			"_",
			"+",
			"=",
			"&",
			"^",
			"%",
			"#",
			"@",
			"`",
			";",
			"$",
			"��",
			"��",
			"����",
			"��",
			"��",
			"��",
			"...",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			"��",
			" "
			));

}
