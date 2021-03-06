package nju.lamda.hadoop;

import java.io.IOException;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.LineRecordReader;

public class CopyFileLineRecordReader extends RecordReader<LongWritable, Text>
{
	private LineRecordReader _lineReader;
	
	CopyFileLineRecordReader()
	{
		_lineReader = new LineRecordReader();
	}
	
	@Override
    public void initialize(InputSplit sp, TaskAttemptContext context)
            throws IOException{
		CopyFileSplitEx split = (CopyFileSplitEx) sp;
		
    	_lineReader.initialize(split.getFileSplit(), context);
    }

	@Override
	public void close() throws IOException {
		// TODO Auto-generated method stub
		_lineReader.close();
	}

	@Override
	public LongWritable getCurrentKey() throws IOException, InterruptedException {
		// TODO Auto-generated method stub
		return _lineReader.getCurrentKey();
	}

	@Override
	public Text getCurrentValue() throws IOException, InterruptedException {
		// TODO Auto-generated method stub
		return _lineReader.getCurrentValue();
	}

	@Override
	public float getProgress() throws IOException, InterruptedException {
		// TODO Auto-generated method stub
		return _lineReader.getProgress();
	}

	@Override
	public boolean nextKeyValue() throws IOException, InterruptedException {
		// TODO Auto-generated method stub
		return _lineReader.nextKeyValue();
	}
}
