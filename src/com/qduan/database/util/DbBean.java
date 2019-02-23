package com.qduan.database.util;

import java.io.File;
import java.io.FileWriter;
import java.security.Principal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import javax.swing.table.DefaultTableModel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.qduan.util.FileUtil;

@ManagedBean(name = "dbbean")
@SessionScoped
public class DbBean {

	String dataFolder = System.getProperty("user.home") + "/data/dbadmin-tool/history";
	Context context;

	Logger logger = LogManager.getLogger("synclogger");
	Logger sql_logger = LogManager.getLogger("table_history");

	private String datasource;

	private String sqlquery;

	private int rows;

	private String result;

	String equery;

	String tablename;
	boolean useOriginalSql;

	String tablehistory;
	List<String> tableNames = new ArrayList<String>();

	FileUtil fileUtil = new FileUtil();

	long timeTaken;

	List<String> primaryKeyColumns = new ArrayList<String>();
	Map<String, String> foreignKeyColumns = new HashMap<String, String>();
	List<String> indexColumns = new ArrayList<String>();

	//List<DbcolumnBean> columnBeans = new ArrayList<DbcolumnBean>();
	String currentUser;

	public DbBean() {
		if (this.currentUser == null) {
			this.currentUser = this.getCurrentUser();
			logger.info("user logged in: " + this.currentUser + " from " + getRemoteIp());
		}
		File f = new File(dataFolder);
		if(!f.exists()) {
			f.mkdirs();
			logger.info("history folder will be saved in " + f.getAbsolutePath());
		}
	}

	// TIP1
	@PostConstruct
	public void init() {
		if (this.datasource == null)
			this.datasource = "UnitDataSource";
		if (this.rows == 0)
			this.rows = 20;
		if (this.sqlquery == null) {
			this.sqlquery = "SELECT * FROM TBMMC_ACCT_ENROLLMENT; ";
		}
		logger.debug("post construct called");
	}

	public String getDatasource() {
		return datasource;
	}

	public void setDatasource(String datasource) {
		logger.debug("set data source=" + datasource);

		if (this.datasource == null || !this.datasource.equals(datasource)) {
			try {
				this.datasource = datasource;
				this.search();
			} catch (Exception e) {
				logger.error(e.getMessage());
				e.printStackTrace();
			}
		}

	}

	public String getSqlquery() {
		return sqlquery;
	}

	public void setSqlquery(String sqlquery) {

		if (sqlquery != null) {
			this.sqlquery = sqlquery.replaceAll("\r", "");
			this.sqlquery = this.sqlquery.replaceAll("\n", "");
		}
		logger.debug("set sql=" + sqlquery);
	}

	protected void logTableHistory(String effectiveQuery) throws Exception {
		if (effectiveQuery == null) {
			return;
		}

		//new File(this.dataFolder).mkdirs();
		String filename = this.getTablehistoryFilename();

		File f = new File(filename);
		if (f.exists()) {
			List<String> existingContents = fileUtil.readFileAsList(filename);
			boolean exists = false;
			for (int i = 0; i < existingContents.size(); i++) {
				if (existingContents.get(i).equalsIgnoreCase(sqlquery)) {
					exists = true;
					break;
				}
			}
			if (exists) {
				logger.debug("query existing in history, skipping writing history");
				return;
			}
		}
		FileWriter fw = new FileWriter(filename, true);
		fw.write(sqlquery + "\r\n");
		logger.debug("query logged: " + sqlquery);
		fw.flush();
		fw.close();
	}

	// select * from TBMBR_NOTIFICATION_INVNTRY;
	// select * from UNIT.TBMBR_NOTIFICATION_INVNTRY
	protected String extractTablenameFromQuery(String query) {

		String sql = query.toUpperCase();
		String from = "FROM ";
		int fromStart = sql.indexOf(from) + from.length();
		int tableStart = 0;
		for (int i = fromStart; i < sql.length(); i++) {
			char c = sql.charAt(i);
			if (c != ' ') //
			{
				tableStart = i;
				break;
			}
		}

		/*- 
		 * 3 casese
		 * 1)  SELECT * FROM TBMMC_PREF_INVNTRY; 
		 * 2) SELECT * FROM TBMMC_PREF_INVNTRY WHERE ...
		 * 3) SELECT * FROM TBMMC_PREF_INVNTRY  
		 * 4) select * from UNIT.TBMMC_PREF_INVNTRY
		 */

		int tableEnd = -1;
		for (int i = tableStart; i < sql.length(); i++) {
			char c = sql.charAt(i);
			/*-
			 * 1) table name can have schema
			 * 2) 
			 */
			if (c != '_' && c != '.' && !Character.isLetter(c)) {
				tableEnd = i;
				break;
			}
		}

		String rval = null;
		if (tableEnd < 0) {
			rval = sql.substring(tableStart).trim();
		} else {
			rval = sql.substring(tableStart, tableEnd).trim();
		}

		// tableEnd = Math.min(tableEnd, tableEnd2);
		// this.setTablename(rval);
		// this.tablename = rval;
		return rval;

	}

	public String getResult() {
		// return "hello world";
		return result;
	}

	public void setResult(String result) {
		logger.debug("set result");
		this.result = result;
	}

	public int getRows() {
		return rows;
	}

	public void setRows(int rows) {
		logger.debug("set row=" + rows);
		if (rows == 0) {
			rows = 20;
		}
		this.rows = rows;
	}

	public void search() throws Exception {
		this.search_init();

		if (sqlquery == null) {
			this.sqlquery = null;
			this.equery = null;
			return;
		}

		this.search_processQuery();

		logger.debug("modified query=" + equery);
		DefaultTableModel rval = search_query();
		this.result = convertToHtmlTable(rval);
	}

	protected DefaultTableModel search_query() throws Exception {
		DefaultTableModel rval = new DefaultTableModel();
		try {
			DataSource ds = (DataSource) context.lookup("jdbc/" + this.datasource);
			logger.debug("data source found");
			Connection connection = ds.getConnection();
			logger.debug("connection retrieved from " + this.datasource);
			DatabaseMetaData dmd = connection.getMetaData();

			this.tablename = this.extractTablenameFromQuery(equery);

			String table = this.tablename;
			String schema = null; // "UNIT"; // hardcode for testing
			if (this.tablename != null) {
				int ind = this.tablename.indexOf(".");
				if (ind > 0) {
					table = tablename.substring(ind + 1);
				}
			}
			if (this.tableNames.contains(table)) {
				this.tableNames.add(table);
			}
			logger.debug("searching column metadata ...");
			this.search_retrieveIndexAndPkFkInfo(dmd, schema, table);
			logger.debug("table = " + table + ", schema=" + schema);

			PreparedStatement statement = connection.prepareStatement(equery);
			ResultSet resultset = null;
			// if (query.startsWith("SELECT")) {
			logger.info("executing query " + equery);
			long t1 = System.currentTimeMillis();
			resultset = statement.executeQuery();
			this.timeTaken = System.currentTimeMillis() - t1;
			logger.info("executing query done.");
			// }
			ResultSetMetaData rsmd = resultset.getMetaData();
			int colCount = rsmd.getColumnCount();
			// print headers
			for (int i = 1; i <= colCount; i++) {
				String columnName = rsmd.getColumnName(i);

				rval.addColumn(columnName);
			}
			// print values
			while (resultset.next()) {
				Object[] rowData = new Object[colCount];
				for (int i = 1; i <= colCount; i++) {
					Object value = resultset.getObject(i);
					rowData[i - 1] = value;
				}
				rval.addRow(rowData);
			}
			resultset.close();
			statement.close();
			connection.close();
			this.logTablename(this.tablename);
			this.logTableHistory(sqlquery);
		} catch (Exception e) {
			this.result = e.getMessage();
			e.printStackTrace();
			logger.error(e.getMessage());
			throw e;
		}
		return rval;

	}

	protected void search_processQuery() throws Exception {
		logger.debug("raw query=" + sqlquery);
		String sql = sqlquery.trim();

		String upperSql = sql.toUpperCase();
		if (upperSql.startsWith("SELECT")) {
			equery = sql.replace(";", "");
			if (!this.useOriginalSql) {
				equery = equery + " FETCH FIRST " + this.rows + " ROWS ONLY;";
			} else {
				equery = equery + ";";
			}
		} else {
			throw new Exception("Only select statement is supported.");
		}
	}

	protected void search_init() throws Exception {
		this.primaryKeyColumns.clear();
		this.foreignKeyColumns.clear();
		this.indexColumns.clear();

		if (context == null) {
			Properties p = new Properties();
			// p.put(Context., value)
			p.put(Context.PROVIDER_URL, "iiop://localhost:2809");
			p.put(Context.INITIAL_CONTEXT_FACTORY, "com.ibm.websphere.naming.WsnInitialContextFactory");
			p.put("com.ibm.websphere.naming.jndicache.cacheobject", "cleared");
			context = new InitialContext();
			logger.info("initial context initialzed.");
		}

	}

	protected void search_retrieveIndexAndPkFkInfo(DatabaseMetaData dmd, String schema, String table) throws Exception {
		ResultSet PK = dmd.getPrimaryKeys(null, schema, table);
		logger.debug("PK.row number= " + PK.getRow()); // row number=0

		/*-
		 * MMC_NTFCATN_NAME===null
		MMC_PRODUCT_ID===null
		MMC_PRODUCT_TYPE===null
		
		 */
		while (PK.next()) {
			// logger.info(PK.getString("COLUMN_NAME") + "===" +
			// PK.getString("PK_NAME"));
			String pkcol = PK.getString("COLUMN_NAME");
			if (!this.primaryKeyColumns.contains(pkcol)) {
				this.primaryKeyColumns.add(pkcol);
			}
		}

		ResultSet FK = dmd.getImportedKeys(null, schema, table);
		logger.debug("FK.row number= " + FK.getRow());
		/*-
		2019-01-07 22:53:43,701 INFO c.q.d.u.DatabaseBean [WebContainer : 4] TBMBR_NOTIFICATION_INVNTRY---MBR_NOTIFICATION_INVT_ID===TBSMS_SHORTCODE_NTFCTNS---SMS_NOTIFICATION_INVT_ID
		2019-01-07 22:53:43,701 INFO c.q.d.u.DatabaseBean [WebContainer : 4] TBMBR_NOTIFICATION_INVNTRY---MBR_NOTIFICATION_INVT_ID===TBSMS_SHORTCODE_NTFCTNS---SMS_NOTIFICATION_INVT_ID
		2019-01-07 22:53:43,701 INFO c.q.d.u.DatabaseBean [WebContainer : 4] TBMBR_NOTIFICATION_INVNTRY---MBR_NOTIFICATION_INVT_ID===TBSMS_SHORTCODE_NTFCTNS---SMS_NOTIFICATION_INVT_ID
		2019-01-07 22:53:43,701 INFO c.q.d.u.DatabaseBean [WebContainer : 4] TBMBR_NOTIFICATION_INVNTRY---MBR_NOTIFICATION_INVT_ID===TBSMS_SHORTCODE_NTFCTNS---SMS_NOTIFICATION_INVT_ID
		
		 */
		while (FK.next()) {
			String link = FK.getString("PKTABLE_NAME") + "." + FK.getString("PKCOLUMN_NAME");
			String key = FK.getString("FKCOLUMN_NAME");

			if (!this.foreignKeyColumns.containsKey(key)) {
				this.foreignKeyColumns.put(key, link);
			}
		}

		ResultSet IND = dmd.getIndexInfo(null, schema, table, true, true);
		while (IND.next()) {
			// logger.debug(IND.getString("COLUMN_NAME"));
			String col = IND.getString("COLUMN_NAME");
			if (col != null && !this.indexColumns.contains(col)) {
				this.indexColumns.add(col);
			}
		}

	}

	protected void logTablename(String tablename) {
		try {
			String filename = this.getTablesFilename();
			File f = new File(filename);
			if (!f.exists()) {
				f.getParentFile().mkdirs();
				fileUtil.writeFile(filename, "");
			}
			List<String> lines = fileUtil.readFileAsList(filename);
			if (!lines.contains(tablename)) {
				fileUtil.writeFile(filename, tablename);
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		}
	}

	protected String convertToHtmlTable(DefaultTableModel model) {
		StringBuffer rval = new StringBuffer();
		rval.append("<div id='sql_result_div'>");
		rval.append("<table>").append("<tr>");
		int colCount = model.getColumnCount();
		// add row number column
		rval.append("<th>No.</th>");
		for (int i = 0; i < colCount; i++) {
			String indicator = "";
			String tooltip = "";
			String colName = model.getColumnName(i);

			if (this.primaryKeyColumns.contains(colName)) {
				indicator = "<img title=\"Primary Key\" src=\"images/primarykey.png\" width=\"15px\" height=\"15px\"/>";
				tooltip = "Primary Key,";
			}
			if (this.foreignKeyColumns.containsKey(colName)) {
				indicator += "<img title=\"Foreign Key\" src=\"images/foreignkey.jpg\" width=\"15px\" height=\"15px\"/>";
				tooltip += "Foreign Key " + this.foreignKeyColumns.get(colName) + ",";
			}
			if (this.indexColumns.contains(colName)) {
				indicator += "<img  title=\"Index Key\" src=\"images/indexkey.jpg\" width=\"15px\" height=\"15px\"/>";
				tooltip += "Indexed";
			}
			if (indicator.length() != 0) {
				colName += indicator;
			}
			rval.append("<th ondblclick='insertIntoTextarea(this);'>").append(colName).append("</th>");
			// rval.append("<th>").append(colName).append("</th>");
		}
		rval.append("</tr>").append("\n");
		int rowCount = model.getRowCount();
		for (int i = 0; i < rowCount; i++) {
			rval.append("<tr>");
			rval.append("<td>").append(i + 1).append("</td>");
			for (int j = 0; j < colCount; j++) {
				rval.append("<td ondblclick=\"insertIntoTextarea(this);\">").append(model.getValueAt(i, j))
						.append("</td>");
				// rval.append("<td>").append(model.getValueAt(i,
				// j)).append("</td>");
			}
			rval.append("</tr>").append("\n");
		}
		rval.append("</table>\n");
		rval.append("</div>");
		return rval.toString();
	}

	protected String getTablehistoryFilename() {
		return this.dataFolder + "/" + this.tablename + ".txt";
	}

	protected String getTablesFilename() {
		return this.dataFolder + "/tablehistory.txt";
	}

	public String getEquery() {
		return equery;
	}

	public void setEquery(String equery) {
		this.equery = equery;
	}

	public String getTablename() {
		return tablename;
	}

	public void setTablename(String tablename) {
		this.tablename = tablename;
	}

	public String getTablehistory() {
		if (tablename == null) {
			tablename = "null";
		}
		String fn = getTablehistoryFilename();
		if (!new File(fn).exists()) {
			return null;
		}
		try {
			String content = this.fileUtil.readFile(fn);
			String[] lines = content.split("\n");
			StringBuffer rval = new StringBuffer();
			for (int i = lines.length - 1; i >= 0; i--) {
				rval.append(lines[i]).append("\r\n");
			}
			return rval.toString();
		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	public void setTablehistory(String tablehistory) {
		this.tablehistory = tablehistory;
	}

	public List<String> getTableNames() {
		String filename = this.getTablesFilename();
		try {
			this.tableNames = fileUtil.readFileAsList(filename);
			java.util.Collections.sort(this.tableNames);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return this.tableNames;
	}

	public void setTableNames(List<String> tableNames) {
		this.tableNames = tableNames;
	}

	public long getTimeTaken() {
		return timeTaken;
	}

	public void setTimeTaken(long timeTaken) {
		this.timeTaken = timeTaken;
	}

	public boolean isUseOriginalSql() {
		return useOriginalSql;
	}

	public void setUseOriginalSql(boolean useOriginalSql) {
		this.useOriginalSql = useOriginalSql;
	}

	private String getCurrentUser() {

		FacesContext fc = FacesContext.getCurrentInstance();
		ExternalContext externalContext = fc.getExternalContext();
		Principal p = externalContext.getUserPrincipal();
		if (p == null) {
			return null;
		}
		return p.getName();

	}

	private String getRemoteIp() {

		FacesContext fc = FacesContext.getCurrentInstance();
		ExternalContext externalContext = fc.getExternalContext();
		HttpServletRequest request = (HttpServletRequest) externalContext.getRequest();

		String ipAddress = request.getHeader("X-FORWARDED-FOR");
		if (ipAddress != null) {
			// cares only about the first IP if there is a list
			ipAddress = ipAddress.replaceFirst(",.*", "");
		} else {
			ipAddress = request.getRemoteAddr();
		}
		return ipAddress;

	}

}
