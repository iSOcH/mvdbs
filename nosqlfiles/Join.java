import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class Join {
	private static final String tableM = "Mitglieder";
	private static final String tableR = "Registrierungen";
	
	public static void main(String[] args) throws ClassNotFoundException, IOException, InterruptedException {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args)
				.getRemainingArgs();
		if (otherArgs.length != 3) {
			System.err.println("Usage: join.jar <in mitglieder> <in registrierungen> <out>");
			System.exit(2);
		}
		Job job = Job.getInstance(conf, "join");
		
		// erstes Argument ist Pfad zu Mitgliedern, darauf soll MitgliederMapper angewendet werden
		MultipleInputs.addInputPath(job, new Path(otherArgs[0]), TextInputFormat.class, MitgliederMapper.class);
		
		// zweites Argument ist Pfad zu Registrierungen, darauf soll RegistrierungenMapper angewendet werden
		MultipleInputs.addInputPath(job, new Path(otherArgs[1]), TextInputFormat.class, RegistrierungenMapper.class);
		
		// Datentypen festlegen
		job.setReducerClass(MRJoinReducer.class);
		job.setMapOutputValueClass(MapPair.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		FileOutputFormat.setOutputPath(job, new Path(otherArgs[2]));
		
		// Job starten und Fertigstellung abwarten
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
	
	public static class MitgliederMapper extends Mapper<LongWritable, Text, Text, MapPair> {
		/**
		 * trennt Eintraege aus Mitglieder-CSV in Key (mnr) und Daten auf, Daten
		 * werden um Information ergaenzt, dass sie aus der Mitglieder-Tabelle stammen
		 */
		@Override
		protected void map(LongWritable key, Text value,
				Mapper<LongWritable, Text, Text, MapPair>.Context context)
				throws IOException, InterruptedException {
			String[] fields = value.toString().split(";");
			
			Text mnr = new Text(fields[0]);
			String record = fields[1] + "\t" + fields[2] + "\t" + fields[3];
			
			context.write(mnr, new MapPair(tableM, record));
		}
	}
	
	public static class RegistrierungenMapper extends Mapper<LongWritable, Text, Text, MapPair> {
		/**
		 * trennt Eintraege aus Registrierungen-CSV in Key (mnr) und Daten auf, Daten
		 * werden um Information ergaenzt, dass sie aus der Registrierungen-Tabelle stammen
		 */
		@Override
		protected void map(LongWritable key, Text value,
				Mapper<LongWritable, Text, Text, MapPair>.Context context)
				throws IOException, InterruptedException {
			String[] fields = value.toString().split(";");
			
			Text mnr = new Text(fields[0]);
			String record = fields[1] + "\t" + fields[2] + "\t" + fields[3];
			
			context.write(mnr, new MapPair(tableR, record));
		}
	}
	
	public static class MRJoinReducer extends Reducer<Text, MapPair, Text, Text> {
		/**
		 * joint Datensaetze aus MitgliederMapper und RegistrierungenMapper
		 * Output: Tab-getrennte Werte;
		 * mnr, felder-aus-mitglieder, felder-aus-registrierungen
		 */
		@Override
		protected void reduce(Text key, Iterable<MapPair> vals,
				Reducer<Text, MapPair, Text, Text>.Context ctx)
				throws IOException, InterruptedException {
			
			// Trennung von Zeilen aus Mitglieder und Registrierungen
			List<String> recordsM = new ArrayList<>();
			List<String> recordsR = new ArrayList<>();
			
			for (MapPair pair : vals) {
				if (tableM.equals(pair.table)) {
					recordsM.add(pair.record);
				} else if (tableR.equals(pair.table)) {
					recordsR.add(pair.record);
				}
			}
			
			// alle Zeilen aus Mitgliedern mit allen Zeilen aus
			// Registrierungen kombinieren
			for (String recordM : recordsM) {
				for (String recordR : recordsR) {
					ctx.write(key, new Text(recordM + "\t" + recordR));
				}
			}
		}
	}

	/**
	 * Hilfsklasse um sowohl eine Table-ID als auch einen Record
	 * von map an reduce weiterreichen zu koennen
	 */
	public static class MapPair implements Writable {
		public String table, record;
		public MapPair(String table, String record) {
			this.table = table; this.record = record;
		}
		
		// default constr wird wegen serialisierbarkeit benoetigt
		public MapPair() {}
		
		@Override
		public void readFields(DataInput in) throws IOException {
			table = in.readUTF(); record = in.readUTF();
		}
		@Override
		public void write(DataOutput out) throws IOException {
			out.writeUTF(table); out.writeUTF(record);
		}
		public static MapPair read(DataInput in) throws IOException {
			final MapPair mp = new MapPair();
			mp.readFields(in); return mp;
		}
	}
}
