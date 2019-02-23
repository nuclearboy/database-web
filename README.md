# database-web
# Introduction
This is a simple utility for running select inquiries from database. Features includes
   * automatically save table name used and available for select
   * save sqls for a specific table. selected a query for this table automatically populates the query section
   * switching environment automatically runs the current query
   * Double click a table header puts the column header name at the cursor position in the query area ---> easier than select header name, copy then paste
   * Double click on a cell puts a quoted value at the cursor location in the query area
   * Drag and drop a cell automatically constructs a where CURRENT_HEARDER IN ('CELL VALUE')

#Preparations
The tool relies on the following datasource names. you need to configure these in your local server. Follow server documentation on how to create data sources.

   * jdbc/UnitDataSource
   * jdbc/IntgDataSource
   * jdbc/PerfDataSource
   * jdbc/ProdDataSource
   
#Other Changes
   * Check log4j2.xml for log location
   
