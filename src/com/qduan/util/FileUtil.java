package com.qduan.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileUtil {
	Logger logger = LogManager.getLogger(FileUtil.class);

	public List<String> readFileAsList(String filename) throws Exception {
		FileReader fr = new FileReader(filename);
		BufferedReader br = new BufferedReader(fr);
		String line;
		List<String> rval = new ArrayList<String>();
		while ((line = br.readLine()) != null) {
			rval.add(line);
		}
		br.close();
		return rval;
	}

	public void writeFile(String filename, List<String> lines) throws Exception {
		File file = new File(filename);
		FileWriter fw = new FileWriter(file, true);
		for(int i=0; i<lines.size(); i++) {
			fw.write(lines.get(i)+"\n");	
		}		
		fw.flush();
		fw.close();
		logger.debug("file written: " + file.getAbsolutePath());
	}

	public String readFile(String filename) throws Exception {
		FileReader fr = new FileReader(filename);
		BufferedReader br = new BufferedReader(fr);
		String line;
		StringBuffer sb = new StringBuffer();
		while ((line = br.readLine()) != null) {
			sb.append(line).append("\n");
		}
		fr.close();
		logger.debug("read file content: " + filename);
		return sb.toString();
	}

	public void writeFile(String filename, String content) throws Exception {
		File file = new File(filename);
		FileWriter fw = new FileWriter(file, true);
		fw.write(content+"\n");
		fw.flush();
		fw.close();
		logger.debug("file written: " + file.getAbsolutePath());
	}

	public boolean fileExistsInFolder(String filename, String fileSuffix, String folder) {
		String absfilename = folder + "/" + filename + "." + fileSuffix;
		return new File(absfilename).exists();
	}

}
