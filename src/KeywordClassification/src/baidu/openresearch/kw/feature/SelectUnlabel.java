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
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import baidu.openresearch.kw.Constants;

public class SelectUnlabel extends Configured implements Tool {
	
	public static class MyMapper extends Mapper<LongWritable, Text, Text, Text> {
		private Text outputKey = new Text();
		private Text outputValue = new Text();

		private char type;

		public void setup(Context context) {
			Configuration conf = context.getConfiguration();

			String filenameTrain = conf.get("filename.train");
			String filenameTest = conf.get("filename.test");
			String filenamePredict = conf.get("filename.predict");

			FileSplit split = (FileSplit) context.getInputSplit();
			String filename = split.getPath().getName();

			if (filenameTrain.indexOf(filename) >= 0) {
				type = 'R';
			} else if (filenameTest.indexOf(filename) >= 0) {
				type = 'E';
			} else if (filenamePredict.indexOf(filename) >= 0) {
				type = 'P';
			}
		}

		public void map(LongWritable key, Text value, Context context)
				throws IOException, InterruptedException {
			
			String line = value.toString();

			int tpos = line.indexOf('\t');
			String id = line.substring(0, tpos);
			String str = line.substring(tpos + 1);

			outputKey.set(id);
			outputValue.set("" + type + str);
			context.write(outputKey, outputValue);
		}
	}

	public static class MyReducer extends
			Reducer<Text, Text, Text, Text> {
		
		Text outputKey = new Text();
		Text outputValue = new Text();
		
		private float thresh;
		
		public void setup(Context context) {
			Configuration conf = context.getConfiguration();
			this.thresh = conf.getFloat("thresh", 1.0f);
		}
		
		@Override
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException 
		{
			String fea = "";
			double conf = -999;
			int label=0;
			for( Text val : values)
			{
				String line = val.toString();
				char ch = line.charAt(0);
				String str = line.substring(1);
				
				if ( ch == 'R')
				{
					outputValue.set(str);
					context.write(key, outputValue);
					break;
				}
				else if (ch == 'E')
				{
					int bpos = str.indexOf(" ");
					fea = str.substring(bpos+1);
				}
				else if (ch == 'P')
				{
					String pair[] = str.split(" ");
					label = Integer.parseInt(pair[0]);
					conf = Double.parseDouble(pair[1]);
				}
			}
			
			if ( conf != -999)
			{
				if ( conf >= thresh)
				{
					outputValue.set("" + label + " " + fea);
					context.write(key, outputValue);
					context.getCounter(SelectUnlabel.class.getName()
							, "numadded").increment(1);
				}
			}
		}
	}

	private String filenameTrain;
	private String filenameTest;
	private String filenamePredict;
	private String filenameResult;
	private String workingDir;
	private float thresh;
	
	private int numAdded;
	
	@Override
	public int run(String[] arg0) throws Exception {
		Configuration conf = getConf();
		Job job = new Job(conf,SelectUnlabel.class.getName());
		job.setJarByClass(SelectUnlabel.class);
        job.setMapperClass(MyMapper.class);
        job.setReducerClass(MyReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        job.setNumReduceTasks(50);
        
        FileInputFormat.addInputPath(job, new Path(this.filenameTrain));
        FileInputFormat.addInputPath(job, new Path(this.filenameTest));
        FileInputFormat.addInputPath(job, new Path(this.filenamePredict));
        
        Path output = new Path(workingDir + "/" + Constants.OUTDIR_TMP);
        
        conf = job.getConfiguration();
        
        FileSystem fs = FileSystem.get(conf);
        fs.deleteOnExit(output);
        fs.close();
        FileOutputFormat.setOutputPath(job, output);
        
        conf.set("filename.train", this.filenameTrain);
        conf.set("filename.test", this.filenameTest);
        conf.set("filename.predict", this.filenamePredict);
        
        conf.setFloat("thresh", (float)this.thresh);
        
        if (!job.waitForCompletion(true))
        {
        	return 1;
        }

        this.numAdded = (int) job.getCounters().findCounter(
        		SelectUnlabel.class.getName(),"numadded").getValue();
        
        UtilsMapReduce.CopyResult(conf, output
        		, new Path(this.filenameResult), false);
        
		return 0;

	}
	
	public int run(String filetrain, String filetest, String filepredict,
			float thr, String fileresult, String workdir) throws Exception
	{
		this.filenameTrain = filetrain;
		this.filenameTest = filetest;
		this.filenamePredict = filepredict;
		this.thresh = thr;
		this.filenameResult = fileresult;
		this.workingDir = workdir;
		
		int retcd = ToolRunner.run(this, null);
		
		System.out.println(this.numAdded);
		
		return retcd;
	}
	
	public static void main(String args[]) throws NumberFormatException, Exception
	{
		(new SelectUnlabel()).run(args[0],args[1],args[2]
				,Float.parseFloat(args[3]), args[4],args[5]);
	}

}
