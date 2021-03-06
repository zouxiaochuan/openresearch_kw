package nju.lamda.hadoop.liblinear;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import nju.lamda.common.Pair;
import nju.lamda.hadoop.UtilsFileSystem;

import org.apache.hadoop.fs.Path;

public class CompareResult 
{
	public static Integer[] getLabel(String filename) throws IOException
	{
		TreeMap<String, Integer> map = new TreeMap<String,Integer>();
		BufferedReader reader = UtilsFileSystem.GetReader(new Path(filename));
		String line;
		
		while( (line = reader.readLine()) != null)
		{
			line = line.trim();
			if ( line.length() > 0)
			{
				PairKeyLabel pair= HadoopSvmIo.GetKeyLabel(line);
				map.put(pair.key, pair.label);
			}
		}
		reader.close();
		
		return map.values().toArray(new Integer[0]);
	}
	
	public static List<Pair<Integer,Double>> getLabelScore(String filename)
			throws IOException
	{
		TreeMap<String, Pair<Integer,Double>> map = 
				new TreeMap<String,Pair<Integer,Double>>();
		BufferedReader reader = UtilsFileSystem.GetReader(new Path(filename));
		String line;
		
		while( (line = reader.readLine()) != null)
		{
			line = line.trim();
			if ( line.length() > 0)
			{
				String parts[] = line.split("[\\t ]");
				int label = Integer.parseInt(parts[1]);
				double score = Double.parseDouble(parts[2]);
				map.put(parts[0], new Pair<Integer,Double>(label,score));
			}
		}
		reader.close();
		
		ArrayList<Pair<Integer,Double>> ret = 
				new ArrayList<Pair<Integer,Double>>();
		ret.addAll(map.values());
		return ret;
		
	}
	
	public static double calcAc(Integer gt[], List<Pair<Integer,Double>> pr
			, double thresh)
	{
		double acc = 0;
		for(int i=0;i<gt.length;i++)
		{
			Pair<Integer,Double> pair = pr.get(i);
			if ( gt[i].equals(pair.v1) && (pair.v2 > thresh))
			{
				acc++;
			}
		}
		
		return acc/gt.length;
	}
	
	public static double calcPr(Integer gt[], List<Pair<Integer,Double>> pr
			,double thresh)
	{
		HashMap<Integer,Integer> map = new HashMap<Integer,Integer>();
		for(int i=0;i<gt.length;i++)
		{
			if ( ! map.containsKey(gt[i]))
			{
				map.put(gt[i], map.size());
			}
		}
		double prs[] = new double[map.size()];
		double tot[] = new double[map.size()];
		
		for(int i=0;i<gt.length;i++)
		{
			Pair<Integer, Double> pair = pr.get(i);
			if ( pair.v2 > thresh)
			{
				tot[map.get(pair.v1)]++;
				
				if ( pair.v1 == gt[i])
				{
					prs[map.get(pair.v1)]++;
				}
			}
		}
		
		double prc = 0;
		double maxpre = 0;;
		for(int i=0;i<map.size();i++)
		{
			prs[i] = prs[i] / tot[i];
			maxpre = Math.max(maxpre, prs[i]);
			
			prc += prs[i];
		}
		
		return prc / map.size();
		//return maxpre;
		
	}
	public static double[] Compare(String filenameGT,String filenamePr
			, double thresh) throws IOException
	{
		Integer labelGt[] = getLabel(filenameGT);	
		List<Pair<Integer,Double>> labelScore = getLabelScore(filenamePr);
		
		double ret[] = new double[2];
		ret[0] = calcAc(labelGt, labelScore, thresh);
		ret[1] = calcPr(labelGt, labelScore, thresh);
		
		return ret;
	}
	
	public static void main(String args[]) throws IOException
	{
		System.out.println(Arrays.toString(Compare(args[0],args[1]
				,Double.parseDouble(args[2]))));
	}
}
