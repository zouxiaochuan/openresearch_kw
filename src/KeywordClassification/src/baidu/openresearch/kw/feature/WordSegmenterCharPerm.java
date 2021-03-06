package baidu.openresearch.kw.feature;

import java.io.IOException;
import java.util.ArrayList;

public class WordSegmenterCharPerm implements WordSegmenterBase {

	private WordSegmenterBase _segmenterChar;
	
	public WordSegmenterCharPerm()
	{
		_segmenterChar = new WordSegmenterIKAnalyzer();
	}
	
	@Override
	public String[] segment(String line) throws IOException {
		String chs[] = _segmenterChar.segment(line);
		ArrayList<String> ret = new ArrayList<String>();
		
		for(int i=0;i<chs.length;i++)
		{
			ret.add(chs[i]);
			
			for(int j=i+1;j<chs.length;j++)
			{
				String str = "";
				if ( chs[i].compareTo(chs[i]) >= 0)
				{
					str = chs[i] + chs[j];
				}
				else
				{
					str = chs[j] + chs[i];
				}
				
				ret.add(str);
			}
		}
		
		return ret.toArray(new String[0]);
	}

}
