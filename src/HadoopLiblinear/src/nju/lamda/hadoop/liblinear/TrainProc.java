package nju.lamda.hadoop.liblinear;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Formatter;

import nju.lamda.common.ArrayOp;
import nju.lamda.hadoop.CopyFileInputFormat;
import nju.lamda.hadoop.UtilsMapReduce;
import nju.lamda.svm.SvmParam;
import nju.lamda.svm.stream.linear.SvcDual;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import de.bwaldvogel.liblinear.SolverType;

public class TrainProc extends Configured implements Tool {
	public int numDim;
	public int numData;
	public int numClass;
	public int bufferSize;
	public int minUpdate;
	public double bias;
	public double c;
	public double eps;
	public String filenameLabel;
	public String filenameModel;
	public String filenameData;
	public String workingDir;
	
	
	public static class MyMapper extends Mapper<IntWritable, Text, Text, Text> {
		private Text k = new Text();
		private Text v = new Text();

		private double c;
		private double bias;
		private double eps;
		private int ndim;
		private int bufferSize;
		private int minUpdate;
		
		private SvmParam param;
		private ArrayList<Integer> labels;
		
		@Override
		public void setup(Context context)
		{
			Configuration conf = context.getConfiguration();
			this.c = conf.getFloat("svm.c", 1.0f);
			this.bias = conf.getFloat("svm.bias", 1.0f);
			this.eps = conf.getFloat("svm.eps", 0.1f);
			this.ndim = conf.getInt("svm.ndim", 0);
			this.bufferSize = conf.getInt("svm.buffersize", 0);
			this.minUpdate = conf.getInt("svm.minupdate", 0);
			
			param = new SvmParam(SolverType.L2R_L2LOSS_SVC_DUAL,
					c, eps, bias);
			
			Path pathLabelfile;
			try {
				pathLabelfile = DistributedCache.getLocalCacheFiles(conf)[0];
				labels = new ArrayList<Integer>();
				BufferedReader reader = new BufferedReader(
						new FileReader(pathLabelfile.toString()));
				
				String line;
				while( (line = reader.readLine())!=null)
				{
					line = line.trim();
					if ( line.length() > 0)
					{
						labels.add(Integer.parseInt(line));
					}
				}
				reader.close();
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		public void map(IntWritable key, Text value, Context context)
				throws IOException, InterruptedException {
			SvmReaderContext svmreader = new SvmReaderContext(context,ndim,bias);
			
			int targetLabel = labels.get(key.get());
			SvcDual solver = new SvcDual();
			double w[] = solver.solve(svmreader, ndim, targetLabel, 
					bufferSize, minUpdate, param);
			k.set("" + targetLabel);
			v.set(ArrayOp.ToString(w, 1e-4));
			context.write(k, v);
		}
	}

	public static class MyReducer extends Reducer<Text, Text, Text, Text> 
	{	
		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			int cnt = 0;
			for( Text val : values)
			{
				context.write(key, val);
				cnt ++;
			}
			
			if ( cnt != 1)
			{
				throw new IOException("svm logic error");
			}
		}
	}

	
	@Override
	public int run(String[] args) throws Exception {
		
		Configuration conf = getConf();
		Job job = new Job(conf,"SVM Train Process");
		job.setJarByClass(TrainProc.class);
        job.setMapperClass(MyMapper.class);
        job.setReducerClass(MyReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.setInputFormatClass(CopyFileInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        
        
        conf = job.getConfiguration();
        conf.setBoolean("mapred.map.tasks.speculative.execution", false);
        
        conf.set("input.copyfile.filename", this.filenameData);
        conf.setInt("input.copyfile.number", this.numClass);
        
        conf.setInt("svm.ndim", this.numDim);
        conf.setInt("svm.buffersize", this.bufferSize);
        conf.setInt("svm.minupdate", this.minUpdate);
        
        conf.setFloat("svm.eps", (float)this.eps);
        conf.setFloat("svm.c", (float)this.c);
        conf.setFloat("svm.bias", (float)this.bias);
        
		DistributedCache.addCacheFile(
				(new Path(this.filenameLabel)).toUri(), job.getConfiguration());
		
        Path output = new Path( this.workingDir + "/" + 
        Constants.OUTDIR_SVM_TRAIN);
        FileSystem fs = FileSystem.get(conf);
        fs.deleteOnExit(output);
        fs.close();
        
        FileOutputFormat.setOutputPath(job, output);
        //UtilsMapReduce.AddLibraryPath(conf, new Path("lib"));
        if (!job.waitForCompletion(true))
        {
        	return 1;
        }
        
        Path pathModel = new Path(filenameModel);
        
        OutputStream res = FileSystem.get(conf).create(pathModel);
        Formatter formatter = new Formatter(res);
        formatter.format("%d %d %f\n", this.numDim, this.numClass, this.bias);
        formatter.flush();
        
        UtilsMapReduce.CopyResult(conf, output, res);
        formatter.close();
        res.close();
        
        return 0;
	}
	
	public int run(String datafile, String labelfile, String modelfile,
			double c, double bias, double eps, int ndim, int buffersize,
			int minupdate, int nclass, String workingDir) throws Exception
	{
		this.filenameData = datafile;
		this.filenameLabel = labelfile;
		this.filenameModel = modelfile;
		this.c = c;
		this.bias = bias;
		this.eps = eps;
		this.numDim = ndim;
		this.numClass = nclass;
		this.bufferSize = buffersize;
		this.minUpdate = minupdate;
		this.workingDir = workingDir;
		
		return ToolRunner.run(this, null);
	}
	
	public static int exec(String datafile, String labelfile, String modelfile,
			double c, double bias, double eps, int ndim, int buffersize,
			int minupdate, int nclass, String workingDir) throws Exception {
		TrainProc proc = new TrainProc();
		return proc.run(datafile, labelfile, modelfile, c, bias, eps, ndim, 
				buffersize, minupdate, nclass, workingDir);
	}
}
