package baidu.openresearch.kw.feature;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.TreeSet;

import nju.lamda.hadoop.UtilsMapReduce;
import nju.lamda.svm.SvmIo;
import nju.lamda.svm.SvmOp;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import de.bwaldvogel.liblinear.FeatureNode;

import baidu.openresearch.kw.Constants;

public class FeatureUsers extends Configured implements Tool {

	public static class MyMapper extends Mapper<LongWritable, Text, Text, Text> {
		private Text outputKey = new Text();
		private Text outputValue = new Text();

		public void setup(Context context)
		{		
		}
		public void map(LongWritable key, Text value, Context context)
				throws IOException, InterruptedException {
			String line = value.toString();
			String pair[] = line.split("\t");
			
			outputKey.set(pair[0]);
			outputValue.set(pair[1]);
			context.write(outputKey, outputValue);
		}
	}
	
	public static class MyReducer extends Reducer<Text, Text, Text, Text> 
	{
		private Text outputValue = new Text();
		
		private HashMap<String, Integer> map;
		
		@Override
		public void setup(Context context)
		{
			Configuration conf = context.getConfiguration();
			map = new HashMap<String,Integer>();
			
			try {
				Path pathDict = DistributedCache.getLocalCacheFiles(conf)[0];
				
				BufferedReader reader = new BufferedReader(
						new FileReader(pathDict.toString()));
				
				while(true)
				{
					String line = reader.readLine();
					if ( line == null)
					{
						break;
					}
					
					String user = line.split("\t")[0].trim();
					if(line.length() > 0)
					{
						if ( !map.containsKey(user))
						{
							map.put(user, map.size()+1);
						}
					}
				}
				
				reader.close();
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
		@Override
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {

			TreeSet<Integer> indx = new TreeSet<Integer>();
			
			for(Text val : values)
			{
				String user = val.toString().trim();
				if ( user.length() > 0)
				{
					Integer ind = map.get(user);
					if ( ind != null)
					{
						indx.add(map.get(user));
					}
				}
			}
			
			FeatureNode x[] = new FeatureNode[indx.size()];
			
			int i=0;
			for( Integer ind : indx)
			{
				x[i++] = new FeatureNode(ind,1.0);
			}
			
			SvmOp.normalizeL2(x);
			outputValue.set(SvmIo.ToString(x));
			
			context.write(key, outputValue);
		}
	}	
	
	public String workingDir;
	public String filenameFeature;
	public String filenameUserDict;
	
	@Override
	public int run(String[] args) throws Exception {
		Configuration conf = getConf();
		Job job = new Job(conf,"FeatureUsers");
		job.setJarByClass(FeatureUsers.class);
        job.setMapperClass(MyMapper.class);
        job.setReducerClass(MyReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        job.setNumReduceTasks(10);
		
        FileInputFormat.addInputPath(job, new Path(Constants.FILE_KEYWORD_USERS));
        
        DistributedCache.addCacheFile(new Path(this.filenameUserDict).toUri()
        		, job.getConfiguration());
        
        Path output = new Path(workingDir + "/" + Constants.OUTDIR_TMP);
        FileSystem fs = FileSystem.get(conf);
        fs.deleteOnExit(output);
        fs.close();
        FileOutputFormat.setOutputPath(job, output);
        
        if (!job.waitForCompletion(true))
        {
        	return 1;
        }
		
        UtilsMapReduce.CopyResult(conf, output
        , new Path(this.filenameFeature), false);
        
		return 0;
	}
	
	public int run(String fileFeature, String fileDict, String workingdir) throws Exception
	{
		this.filenameFeature = fileFeature;
		this.filenameUserDict = fileDict;
		this.workingDir = workingdir;
		
		return ToolRunner.run(this, null);
	}
	
	public static void main(String args[]) throws Exception
	{
		new FeatureUsers().run(args[0],args[1],args[2]);
	}
}
