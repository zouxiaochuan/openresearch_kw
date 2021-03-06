package nju.lamda.hadoop.feaext;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import nju.lamda.hadoop.UtilsFileSystem;
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


public class GenerateDictProc extends Configured implements Tool {
	
	private static final double thresh = Math.log(1.0/0.9);
	
	public static class MyMapper extends
			Mapper<LongWritable, Text, Text, LongWritable> {
		private Text outputKey = new Text();
		private LongWritable outputValue = new LongWritable();

		public void setup(Context context) {

		}

		public void map(LongWritable key, Text value, Context context)
				throws IOException, InterruptedException {
			String line = value.toString();
			line = line.trim();
			if (line.length() > 0) {
				int tpos = line.indexOf('\t');
				//String id = line.substring(0, tpos);
				String words[] = line.substring(tpos + 1).split(" ");

				HashSet<String> wset = new HashSet<String>();
				wset.addAll(Arrays.asList(words));

				for (String w : wset) {
					if (w.length() > 0) {
						outputKey.set(w);
						outputValue.set(1);
						context.write(outputKey, outputValue);
					}
				}
				context.getCounter(GenerateDictProc.class.getName(), "nrec")
						.increment(1);
			}
		}
	}

	public static class MyReducer extends
			Reducer<Text, LongWritable, Text, DoubleWritable> {
		private DoubleWritable outputValue = new DoubleWritable();
		private int mindf;

		public void setup(Context context) {
			Configuration conf = context.getConfiguration();
			this.mindf = conf.getInt("gendict.mindf", 2);
		}

		public void reduce(Text key, Iterable<LongWritable> values,
				Context context) throws IOException, InterruptedException {

//			if ( StopWords.IsStopWords(key.toString()))
//			{
//				return;
//			}
			
			double cnt = 0.0;
			for (LongWritable val : values) {
				cnt += val.get();
			}

			if (cnt >= this.mindf) {
				outputValue.set(cnt);
				context.write(key, outputValue);
			}
		}
	}

	String filenameText;
	String filenameDict;
	int mindf;
	String workingDir;
	
	long numRecord;
	
	@Override
	public int run(String[] arg0) throws Exception {
		Configuration conf = getConf();
		Job job = new Job(conf, GenerateDictProc.class.getName());
		job.setJarByClass(GenerateDictProc.class);
		job.setMapperClass(MyMapper.class);
		job.setReducerClass(MyReducer.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(DoubleWritable.class);
		job.setMapOutputValueClass(LongWritable.class);
		job.setInputFormatClass(TextInputFormat.class);
		job.setOutputFormatClass(TextOutputFormat.class);
		job.setNumReduceTasks(50);

        FileInputFormat.addInputPath(job, new Path(this.filenameText));
        
        Path output = new Path(workingDir + "/" + Constants.OUTDIR_TMP);
        
        conf = job.getConfiguration();
        
        FileSystem fs = FileSystem.get(conf);
        fs.deleteOnExit(output);
        fs.close();
        FileOutputFormat.setOutputPath(job, output);
        conf.setInt("gendict.mindf",this.mindf);

		if (!job.waitForCompletion(true)) {
			return 1;
		}

		this.numRecord = job.getCounters().findCounter(
				GenerateDictProc.class.getName(),"nrec").getValue();
		UtilsMapReduce.CopyResult(conf, output, new Path(
				workingDir + "/" +Constants.TMP_DICT)
			, false);

		return 0;
	}
	
	public int run(String filetext, String filedict, int mindf, String workdir) 
			throws Exception
	{
		this.filenameText = filetext;
		this.filenameDict = filedict;
		this.mindf = mindf;
		this.workingDir = workdir;
		
		int retcd = ToolRunner.run(this, null);
		if ( retcd != 0)
		{
			return retcd;
		}
		
		BufferedReader r =UtilsFileSystem.GetReader(new Path(Constants.TMP_DICT));
		BufferedWriter w =UtilsFileSystem.GetWriter(new Path(this.filenameDict));
		
		String line;
		int cnt = 1;
		while( (line = r.readLine())!=null)
		{
			String parts[] = line.split("\t");
			double idf =  Math.log(numRecord/Double.parseDouble(parts[1]));
			if ( idf > thresh)
			{
				w.write("" + cnt + "\t" + parts[0] + "\t" + idf + "\n");
				cnt++;
			}
		}
		
		r.close();
		w.close();
		return 0;
	}
}
