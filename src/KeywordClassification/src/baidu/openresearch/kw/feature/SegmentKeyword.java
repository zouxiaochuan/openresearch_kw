package baidu.openresearch.kw.feature;

import java.io.IOException;

import nju.lamda.hadoop.UtilsMapReduce;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
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

import baidu.openresearch.kw.Constants;

public class SegmentKeyword extends Configured implements Tool
{
	public static class MyMapper extends Mapper<LongWritable, 
	Text, LongWritable, Text> {
		private LongWritable outputKey = new LongWritable();
		private Text outputValue = new Text();
		private WordSegmenterBase segmenter;

		public void setup(Context context)
		{		
			Configuration conf = context.getConfiguration();
			
			int type = conf.getInt("segmenter.type", 0);
			if ( type == 0)
			{
				segmenter = new WordSegmenterIKAnalyzer();
			}
			else if ( type == 1)
			{
				segmenter = new WordSegmenterChar();
			}
			else if ( type == 2)
			{
				segmenter = new WordSegmenterCharNGram(4);
			}
			else if ( type == 3)
			{
				segmenter = new WordSegmenterCharPerm();
			}
		}
		public void map(LongWritable key, Text value, Context context)
				throws IOException, InterruptedException {
			String line = value.toString();
			line = line.trim();
			if ( line.length() > 0)
			{
				String pair[] = line.split("\t");
				outputKey.set(Long.parseLong(pair[0]));
				
				if ( pair.length == 2)
				{
					String seg[] = segmenter.segment(pair[1]);
					
					StringBuffer buffer = new StringBuffer();
					int cnt =0;
					for( String w : seg)
					{
						if ( cnt++>0)
						{
							buffer.append(' ');
						}
						buffer.append(w);
					}
					
					outputValue.set(buffer.toString());
				}
				else
				{//only label
					
				}
				
				context.write(outputKey, outputValue);
			}
		}
	}
	
	public static class MyReducer extends Reducer<LongWritable, Text,
		LongWritable, Text> 
	{	
		public void reduce(LongWritable key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			context.write(key, values.iterator().next());
		}
	}	
	
	String workingDir;
	String filenameKeyword;
	String filenameSegmented;
	int type;
	
	@Override
	public int run(String[] args) throws Exception {
		Configuration conf = getConf();
		Job job = new Job(conf,"SegmentKeyword");
		job.setJarByClass(SegmentKeyword.class);
        job.setMapperClass(MyMapper.class);
        job.setReducerClass(MyReducer.class);
        job.setOutputKeyClass(LongWritable.class);
        job.setOutputValueClass(Text.class);
        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        job.setNumReduceTasks(50);
        
        FileInputFormat.addInputPath(job, new Path(this.filenameKeyword));
        FileInputFormat.setMaxInputSplitSize(job, 1024*1024*16);
        
        Path output = new Path(workingDir + "/" + Constants.OUTDIR_TMP);
        
        conf = job.getConfiguration();
        
        FileSystem fs = FileSystem.get(conf);
        fs.deleteOnExit(output);
        fs.close();
        FileOutputFormat.setOutputPath(job, output);
        
        conf.setInt("segmenter.type", this.type);
        
        //UtilsMapReduce.AddLibraryPath(conf, new Path("lib"));
        
        if (!job.waitForCompletion(true))
        {
        	return 1;
        }
		
        UtilsMapReduce.CopyResult(conf, output
        		, new Path(this.filenameSegmented), false);
        
		return 0;
	}

	public int run(String filekeyword, String fileseg, 
			int type, String workingdir) throws Exception
	{
		this.filenameKeyword = filekeyword;
		this.filenameSegmented = fileseg;
		this.type = type;
		this.workingDir = workingdir;
		
		return ToolRunner.run(this, null);
	}
	
	public static void main(String args[]) throws Exception
	{
		(new SegmentKeyword()).run(args[0],args[1],Integer.parseInt(args[2])
			,args[3]);
	}
}
