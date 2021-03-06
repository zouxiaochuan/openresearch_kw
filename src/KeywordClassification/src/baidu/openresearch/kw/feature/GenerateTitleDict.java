package baidu.openresearch.kw.feature;

import java.io.IOException;
import java.util.HashSet;

import nju.lamda.hadoop.UtilsMapReduce;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
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

public class GenerateTitleDict extends Configured implements Tool {
	public static class MyMapper extends
			Mapper<LongWritable, Text, Text, LongWritable> {
		private Text outputKey = new Text();
		private LongWritable outputValue = new LongWritable();

		public void setup(Context context) {

		}

		public void map(LongWritable key, Text value, Context context)
				throws IOException, InterruptedException {
			String line = value.toString();
			if (line.length() > 0) {
				String pair[] = line.split("\t");

				if ((pair.length == 11) || (pair.length == 10) 
						|| (pair.length == 9)) {
					HashSet<String> set  = new HashSet<String>();
					for (int i = 1; i < pair.length; i++) {
						String words[] = pair[i].split("" + ((char) 17));
						for (String w : words) {
							if ( w.length() > 0)
							{
								set.add(w);
							}
						}
					}
					
					for(String w : set)
					{
						outputKey.set(w);
						outputValue.set(1);
						
						context.write(outputKey, outputValue);
					}
				} else {
					throw new IOException("part lenght must be 11");
				}
			}
		}
	}

	public static class MyReducer extends
			Reducer<Text, LongWritable, Text, DoubleWritable> {
		private DoubleWritable outputValue = new DoubleWritable();
		private double nD;

		public void setup(Context context) {
			nD = context.getConfiguration().getLong("keyword.nrec", 1);
		}

		public void reduce(Text key, Iterable<LongWritable> values,
				Context context) throws IOException, InterruptedException {

			double cnt = 0.0;
			for (LongWritable val : values) {
				cnt += val.get();
			}

			if (cnt > 1) {
				outputValue.set(Math.log(nD / cnt));
				context.write(key, outputValue);
			}
		}
	}

	String filenameTitle;
	String filenameResult;
	int nrec;
	String workingDir;
	
	@Override
	public int run(String[] arg0) throws Exception {
		Configuration conf = getConf();
		Job job = new Job(conf, GenerateTitleDict.class.getName());
		job.setJarByClass(GenerateTitleDict.class);
		job.setMapperClass(MyMapper.class);
		job.setReducerClass(MyReducer.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(DoubleWritable.class);
		job.setMapOutputValueClass(LongWritable.class);
		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(TextOutputFormat.class);
		job.setNumReduceTasks(50);

        FileInputFormat.addInputPath(job, new Path(this.filenameTitle));
        
        Path output = new Path(workingDir + "/" + Constants.OUTDIR_TMP);
        
        conf = job.getConfiguration();
        
        conf.setLong("keyword.nrec", nrec);
        
        FileSystem fs = FileSystem.get(conf);
        fs.deleteOnExit(output);
        fs.close();
        FileOutputFormat.setOutputPath(job, output);

		if (!job.waitForCompletion(true)) {
			return 1;
		}

		UtilsMapReduce.CopyResult(conf, output, new Path(this.filenameResult)
			, false);

		return 0;
	}
	
	public int run(String filetitle, String fileresult, int n, String workdir) throws Exception
	{
		this.filenameTitle = filetitle;
		this.filenameResult = fileresult;
		this.nrec = n;
		this.workingDir = workdir;
		
		return ToolRunner.run(this, null);
	}
	
	public static void main(String args[]) throws NumberFormatException, Exception
	{
		(new GenerateTitleDict()).run(args[0],args[1],Integer.parseInt(args[2]),
				args[3]);
	}

}
