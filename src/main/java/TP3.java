/**
 * @author David Auber 
 * @date 07/10/2016
 * Ma�tre de conf�rencces HDR
 * LaBRI: Universit� de Bordeaux
 */

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Iterator;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import bigdata.worldpop.ResumeCities.CityResume;

public class TP3_without_median extends Configured implements Tool {

	public static class CityLevel implements Writable, Cloneable{
		long count;
		long sum;
		long max;
		long min;

		CityLevel(){
			count=sum=max=min=0;
		}

		CityLevel(int population){
			sum=max=min=population;
			count=1;
		}
		void combine(CityLevel other){
			if(other.min<min||count==0) min=other.min;
			if(other.max>max) max=other.max;
			sum+=other.sum;
			count+=other.count;
		}

		double average(){
			return sum/count;
		}

		public void write(DataOutput out) throws IOException {
			out.writeLong(min);
			out.writeLong(max);
			out.writeLong(sum);
			out.writeLong(count);
		}

		public void readFields(DataInput in) throws IOException {
			min   = in.readLong();
			max   = in.readLong();
			sum   = in.readLong();
			count = in.readLong();
		}

		public String toString() {
			StringBuilder tmp = new StringBuilder();
			tmp.append(count);
			tmp.append(",");
			tmp.append(min);
			tmp.append(",");
			tmp.append(max);
			tmp.append(",");
			tmp.append(average());
			return tmp.toString();
		}

		public CityLevel clone() {
			try {
				return (CityLevel)super.clone();
			}
			catch (Exception e) {
				System.err.println(e.getStackTrace());
				System.exit(-1);
			}
			return null;
		}

	}

	public static class CitiesMapper	extends Mapper<Object, Text, IntWritable, CityLevel> {
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
			String tokens[] = value.toString().split(",");
			if (tokens.length < 7 || tokens[4].length()==0) return;
			int pop = 0;
			try {
				pop = Integer.parseInt(tokens[4]);
			}
			catch(Exception e) {				
				return;
			}
			double level=Math.floor(Math.log10(pop));
			context.write(new IntWritable((int) level),new CityLevel(pop));
		}
	}

	public static class CitiesReducer extends Reducer<IntWritable,CityLevel,Text,Text> {
		public void reduce(IntWritable key, Iterable<CityLevel> values,Context context) throws IOException, InterruptedException {
			CityLevel level=CitiesCombiner.merge(values)
			context.write(new Text(Double.toString(Math.pow(10,key.get()))),new Text(level.toString()));
		}
	}

	public static class CitiesCombiner extends Reducer<IntWritable,CityLevel,Text,Text> {
		public static CityLevel merge(Iterable<CityResume> values){
			CityLevel level = new CityLevel();
			Iterator<CityResume> iterator=values.iterator();
			while(iterator.hasNext()) level.merge(iterator.next())
			return level;
		}

		public void reduce(IntWritable key, Iterable<CityLevel> values,Context context) throws IOException, InterruptedException {
			CityLevel level=merge(values);
			if(level!=null) context.write(key,level);
		}
	}

	public int run(String args[]) throws IOException, ClassNotFoundException, InterruptedException {
		Configuration conf = getConf();
		Job job = Job.getInstance(conf, "Summary of population");
		job.setJarByClass(TP3_without_median.class);
		
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		job.setOutputFormatClass(TextOutputFormat.class);
		job.setInputFormatClass(TextInputFormat.class);
		job.setNumReduceTasks(1);
		try {
			FileInputFormat.addInputPath(job, new Path(args[0]));
			FileOutputFormat.setOutputPath(job, new Path(args[1]));
		} 
		catch (Exception e) {
			System.out.println(" bad arguments, waiting for 2 arguments [inputURI] [outputURI]");
			return -1;
		}
		job.setMapperClass(CitiesMapper.class);
		job.setMapOutputKeyClass(IntWritable.class);
		job.setMapOutputValueClass(CityLevel.class);
		job.setCombinerClass(CitiesCombiner.class);
		job.setReducerClass(CitiesReducer.class);

		return job.waitForCompletion(true) ? 0 : 1;
	}

	public static void main(String args[]) throws Exception {
		System.exit(ToolRunner.run(new TP3(), args));
	}



}
