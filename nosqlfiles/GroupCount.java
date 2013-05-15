import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class GroupCount {
	
	public static void main(String[] args) throws IOException, ClassNotFoundException, InterruptedException {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args)
				.getRemainingArgs();
		if (otherArgs.length != 2) {
			System.err.println("Usage: groupcount.jar <in> <out>");
			System.exit(2);
		}
		Job job = new Job(conf, "groupcount");
		job.setMapperClass(GCountMapper.class);
		job.setReducerClass(GCountReducer.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(LongWritable.class);
		FileInputFormat.addInputPath(job, new Path(otherArgs[0]));
		FileOutputFormat.setOutputPath(job, new Path(otherArgs[1]));
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
	
	public static class GCountMapper extends Mapper<LongWritable, Text, Text, LongWritable> {
		private static final LongWritable one = new LongWritable(1);
		@Override
		protected void map(LongWritable key, Text val,
				org.apache.hadoop.mapreduce.Mapper<LongWritable, Text, Text, LongWritable>.Context ctx)
				throws IOException, InterruptedException {
			String line = val.toString();
			String[] fields = line.split(";");
			ctx.write(new Text(fields[0]), one);
		}
	}
	
	public static class GCountReducer extends Reducer<Text, LongWritable, Text, LongWritable> {
		@Override
		protected void reduce(Text key, Iterable<LongWritable> vals,
				org.apache.hadoop.mapreduce.Reducer<Text, LongWritable, Text, LongWritable>.Context ctx)
				throws IOException, InterruptedException {
			long count = 0;
			for (LongWritable val : vals) count++;
			ctx.write(key, new LongWritable(count));
		}
	}

}
