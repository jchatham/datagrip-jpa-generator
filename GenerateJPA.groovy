import com.intellij.database.model.DasTable
import com.intellij.database.util.Case
import com.intellij.database.util.DasUtil

/*
 * Available context bindings:
 *   SELECTION   Iterable<DasObject>
 *   PROJECT     project
 *   FILES       files helper
 */

packageName = "com.sample;"
typeMapping = [
        (~/(?i)int/)                      : "Long",
        (~/(?i)numeric|money/)            : "java.util.BigDecimal",
        (~/(?i)float|double|decimal|real/): "Double",
        (~/(?i)datetime|timestamp/)       : "java.sql.Timestamp",
        (~/(?i)date/)                     : "java.sql.Date",
        (~/(?i)time/)                     : "java.sql.Time",
        (~/(?i)boolean/)                  : "Boolean",
        (~/(?i)/)                         : "String"
]

FILES.chooseDirectoryAndSave("Choose directory", "Choose where to store generated files") { dir ->
    SELECTION.filter { it instanceof DasTable }.each { generate(it, dir) }
}

def generate(table, dir) {
    def tableName = table.getName()
    def className = javaName(tableName, true)
    def fields = calcFields(table)
    new File(dir, className + ".java").withPrintWriter { out -> generate(out, className, fields, tableName) }
}


def generate(out, className, fields, tableName) {
    fkRemovalString = "Id"
    out.println "package $packageName"
    out.println ""
    out.println ""
    out.println "import javax.persistence.*;"
    out.println "import lombok.Data;"
    out.println "import javax.persistence.GeneratedValue;"
    out.println "import javax.persistence.GenerationType;"
    out.println "import javax.persistence.SequenceGenerator;"
    out.println ""
    out.println "@Entity"
    out.println "@Table(name=\"${tableName}\")"
    out.println "@Data"
    out.println "public class ${underscoreToCamelCase(className)} {"
    out.println ""
    fields.each() {
        out.println ""
        //comment line
        out.println "  //${it.comment}"

        if (it.primary) {
            generatorName = "${it.colName}_generator"
            //this is the postgres sequence naming standard
            dbSeqName = "${tableName}_${it.colName}_seq"
            out.println "  @Id"
            out.println "  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = \"${generatorName}\")"
            out.println "  @SequenceGenerator(name = \"${generatorName}\", sequenceName = \"${dbSeqName}\", allocationSize = 1)"
        }

        //fk defaults to manytoone - would be nice to have manytomany detection
        if (it.foreign) {
            out.println "  @ManyToOne(fetch = FetchType.LAZY)"
            out.println "  @JoinColumn(name=\"${it.colName}\")"
            out.println "  private ${it.name.capitalize().minus(fkRemovalString)} ${underscoreToCamelCase(it.name)};"
        } else {
            out.println "  @Column(name=\"${it.colName}\")"
            out.println "  private ${it.type} ${underscoreToCamelCase(it.name)};"
        }
    }
    out.println ""
    out.println "}"

}

def calcFields(table) {
    DasUtil.getColumns(table).reduce([]) { fields, col ->
        def spec = Case.LOWER.apply(col.getDataType().getSpecification())
        def typeStr = typeMapping.find { p, t -> p.matcher(spec).find() }.value
        fields += [[
                           name   : javaName(col.getName(), false),
                           type   : typeStr,
                           comment: col.comment ? "" : "Column ${col.getName()} of data type ${col.getDataType()}.",
                           colName: col.getName(),
                           primary: DasUtil.isPrimary(col),
                           foreign: DasUtil.isForeign(col)
                   ]]
    }
}

def javaName(str, capitalize) {
    def s = com.intellij.psi.codeStyle.NameUtil.splitNameIntoWords(str)
            .collect { Case.LOWER.apply(it).capitalize() }
            .join("")
            .replaceAll(/[^\p{javaJavaIdentifierPart}[_]]/, "_")
    capitalize || s.length() == 1 ? s : Case.LOWER.apply(s[0]) + s[1..-1]
}

static String underscoreToCamelCase(String underscore) {
    if (!underscore || underscore.isAllWhitespace()) {
        return ''
    }
    return underscore.replaceAll(/_\w/) { it[1].toUpperCase() }
}
