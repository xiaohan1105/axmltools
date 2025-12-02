package red.jiuzhou.util;

import cn.hutool.core.io.FileUtil;

import java.io.File;
import java.io.IOException;

public class XmlStringModifier {

    private static String DOCTYPE = "<!DOCTYPE strings [\n" +
            "\t\t<!ENTITY quot \"quot\">\n" +
            "\t\t<!ENTITY amp \"amp\">\n" +
            "\t\t<!ENTITY apos \"apos\">\n" +
            "\t\t<!ENTITY lt \"lt\">\n" +
            "\t\t<!ENTITY gt \"gt\">\n" +
            "\t\t<!ENTITY nbsp \"nbsp\">\n" +
            "\t\t<!ENTITY iexcl \"iexcl\">\n" +
            "\t\t<!ENTITY cent \"cent\">\n" +
            "\t\t<!ENTITY pound \"pound\">\n" +
            "\t\t<!ENTITY curren \"curren\">\n" +
            "\t\t<!ENTITY yen \"yen\">\n" +
            "\t\t<!ENTITY brvbar \"brvbar\">\n" +
            "\t\t<!ENTITY sect \"sect\">\n" +
            "\t\t<!ENTITY uml \"uml\">\n" +
            "\t\t<!ENTITY copy \"copy\">\n" +
            "\t\t<!ENTITY ordf \"ordf\">\n" +
            "\t\t<!ENTITY laquo \"laquo\">\n" +
            "\t\t<!ENTITY not \"not\">\n" +
            "\t\t<!ENTITY shy \"shy\">\n" +
            "\t\t<!ENTITY reg \"reg\">\n" +
            "\t\t<!ENTITY macr \"macr\">\n" +
            "\t\t<!ENTITY deg \"deg\">\n" +
            "\t\t<!ENTITY plusmn \"plusmn\">\n" +
            "\t\t<!ENTITY sup2 \"sup2\">\n" +
            "\t\t<!ENTITY sup3 \"sup3\">\n" +
            "\t\t<!ENTITY acute \"acute\">\n" +
            "\t\t<!ENTITY micro \"micro\">\n" +
            "\t\t<!ENTITY para \"para\">\n" +
            "\t\t<!ENTITY middot \"middot\">\n" +
            "\t\t<!ENTITY cedil \"cedil\">\n" +
            "\t\t<!ENTITY sup1 \"sup1\">\n" +
            "\t\t<!ENTITY ordm \"ordm\">\n" +
            "\t\t<!ENTITY raquo \"raquo\">\n" +
            "\t\t<!ENTITY frac14 \"frac14\">\n" +
            "\t\t<!ENTITY frac12 \"frac12\">\n" +
            "\t\t<!ENTITY frac34 \"frac34\">\n" +
            "\t\t<!ENTITY iquest \"iquest\">\n" +
            "\t\t<!ENTITY Agrave \"Agrave\">\n" +
            "\t\t<!ENTITY Aacute \"Aacute\">\n" +
            "\t\t<!ENTITY Acirc \"Acirc\">\n" +
            "\t\t<!ENTITY Atilde \"Atilde\">\n" +
            "\t\t<!ENTITY Auml \"Auml\">\n" +
            "\t\t<!ENTITY Aring \"Aring\">\n" +
            "\t\t<!ENTITY AElig \"AElig\">\n" +
            "\t\t<!ENTITY Ccedil \"Ccedil\">\n" +
            "\t\t<!ENTITY Egrave \"Egrave\">\n" +
            "\t\t<!ENTITY Eacute \"Eacute\">\n" +
            "\t\t<!ENTITY Ecirc \"Ecirc\">\n" +
            "\t\t<!ENTITY Euml \"Euml\">\n" +
            "\t\t<!ENTITY Igrave \"Igrave\">\n" +
            "\t\t<!ENTITY Iacute \"Iacute\">\n" +
            "\t\t<!ENTITY Icirc \"Icirc\">\n" +
            "\t\t<!ENTITY Iuml \"Iuml\">\n" +
            "\t\t<!ENTITY ETH \"ETH\">\n" +
            "\t\t<!ENTITY Ntilde \"Ntilde\">\n" +
            "\t\t<!ENTITY Ograve \"Ograve\">\n" +
            "\t\t<!ENTITY Oacute \"Oacute\">\n" +
            "\t\t<!ENTITY Ocirc \"Ocirc\">\n" +
            "\t\t<!ENTITY Otilde \"Otilde\">\n" +
            "\t\t<!ENTITY Ouml \"Ouml\">\n" +
            "\t\t<!ENTITY times \"times\">\n" +
            "\t\t<!ENTITY Oslash \"Oslash\">\n" +
            "\t\t<!ENTITY Ugrave \"Ugrave\">\n" +
            "\t\t<!ENTITY Uacute \"Uacute\">\n" +
            "\t\t<!ENTITY Ucirc \"Ucirc\">\n" +
            "\t\t<!ENTITY Uuml \"Uuml\">\n" +
            "\t\t<!ENTITY Yacute \"Yacute\">\n" +
            "\t\t<!ENTITY THORN \"THORN\">\n" +
            "\t\t<!ENTITY szlig \"szlig\">\n" +
            "\t\t<!ENTITY agrave \"agrave\">\n" +
            "\t\t<!ENTITY aacute \"aacute\">\n" +
            "\t\t<!ENTITY acirc \"acirc\">\n" +
            "\t\t<!ENTITY atilde \"atilde\">\n" +
            "\t\t<!ENTITY auml \"auml\">\n" +
            "\t\t<!ENTITY aring \"aring\">\n" +
            "\t\t<!ENTITY aelig \"aelig\">\n" +
            "\t\t<!ENTITY ccedil \"ccedil\">\n" +
            "\t\t<!ENTITY egrave \"egrave\">\n" +
            "\t\t<!ENTITY eacute \"eacute\">\n" +
            "\t\t<!ENTITY ecirc \"ecirc\">\n" +
            "\t\t<!ENTITY euml \"euml\">\n" +
            "\t\t<!ENTITY igrave \"igrave\">\n" +
            "\t\t<!ENTITY iacute \"iacute\">\n" +
            "\t\t<!ENTITY icirc \"icirc\">\n" +
            "\t\t<!ENTITY iuml \"iuml\">\n" +
            "\t\t<!ENTITY eth \"eth\">\n" +
            "\t\t<!ENTITY ntilde \"ntilde\">\n" +
            "\t\t<!ENTITY ograve \"ograve\">\n" +
            "\t\t<!ENTITY oacute \"oacute\">\n" +
            "\t\t<!ENTITY ocirc \"ocirc\">\n" +
            "\t\t<!ENTITY otilde \"otilde\">\n" +
            "\t\t<!ENTITY ouml \"ouml\">\n" +
            "\t\t<!ENTITY divide \"divide\">\n" +
            "\t\t<!ENTITY oslash \"oslash\">\n" +
            "\t\t<!ENTITY ugrave \"ugrave\">\n" +
            "\t\t<!ENTITY uacute \"uacute\">\n" +
            "\t\t<!ENTITY ucirc \"ucirc\">\n" +
            "\t\t<!ENTITY uuml \"uuml\">\n" +
            "\t\t<!ENTITY yacute \"yacute\">\n" +
            "\t\t<!ENTITY thorn \"thorn\">\n" +
            "\t\t<!ENTITY yuml \"yuml\">\n" +
            "\t\t<!ENTITY OElig \"OElig\">\n" +
            "\t\t<!ENTITY oelig \"oelig\">\n" +
            "\t\t<!ENTITY Scaron \"Scaron\">\n" +
            "\t\t<!ENTITY scaron \"scaron\">\n" +
            "\t\t<!ENTITY Yuml \"Yuml\">\n" +
            "\t\t<!ENTITY fnof \"fnof\">\n" +
            "\t\t<!ENTITY circ \"circ\">\n" +
            "\t\t<!ENTITY tilde \"tilde\">\n" +
            "\t\t<!ENTITY Alpha \"Alpha\">\n" +
            "\t\t<!ENTITY Beta \"Beta\">\n" +
            "\t\t<!ENTITY Gamma \"Gamma\">\n" +
            "\t\t<!ENTITY Delta \"Delta\">\n" +
            "\t\t<!ENTITY Epsilon \"Epsilon\">\n" +
            "\t\t<!ENTITY Zeta \"Zeta\">\n" +
            "\t\t<!ENTITY Eta \"Eta\">\n" +
            "\t\t<!ENTITY Theta \"Theta\">\n" +
            "\t\t<!ENTITY Iota \"Iota\">\n" +
            "\t\t<!ENTITY Kappa \"Kappa\">\n" +
            "\t\t<!ENTITY Lambda \"Lambda\">\n" +
            "\t\t<!ENTITY Mu \"Mu\">\n" +
            "\t\t<!ENTITY Nu \"Nu\">\n" +
            "\t\t<!ENTITY Xi \"Xi\">\n" +
            "\t\t<!ENTITY Omicron \"Omicron\">\n" +
            "\t\t<!ENTITY Pi \"Pi\">\n" +
            "\t\t<!ENTITY Rho \"Rho\">\n" +
            "\t\t<!ENTITY Sigma \"Sigma\">\n" +
            "\t\t<!ENTITY Tau \"Tau\">\n" +
            "\t\t<!ENTITY Upsilon \"Upsilon\">\n" +
            "\t\t<!ENTITY Phi \"Phi\">\n" +
            "\t\t<!ENTITY Chi \"Chi\">\n" +
            "\t\t<!ENTITY Psi \"Psi\">\n" +
            "\t\t<!ENTITY Omega \"Omega\">\n" +
            "\t\t<!ENTITY alpha \"alpha\">\n" +
            "\t\t<!ENTITY beta \"beta\">\n" +
            "\t\t<!ENTITY gamma \"gamma\">\n" +
            "\t\t<!ENTITY delta \"delta\">\n" +
            "\t\t<!ENTITY epsilon \"epsilon\">\n" +
            "\t\t<!ENTITY zeta \"zeta\">\n" +
            "\t\t<!ENTITY eta \"eta\">\n" +
            "\t\t<!ENTITY theta \"theta\">\n" +
            "\t\t<!ENTITY iota \"iota\">\n" +
            "\t\t<!ENTITY kappa \"kappa\">\n" +
            "\t\t<!ENTITY lambda \"lambda\">\n" +
            "\t\t<!ENTITY mu \"mu\">\n" +
            "\t\t<!ENTITY nu \"nu\">\n" +
            "\t\t<!ENTITY xi \"xi\">\n" +
            "\t\t<!ENTITY omicron \"omicron\">\n" +
            "\t\t<!ENTITY pi \"pi\">\n" +
            "\t\t<!ENTITY rho \"rho\">\n" +
            "\t\t<!ENTITY sigmaf \"sigmaf\">\n" +
            "\t\t<!ENTITY sigma \"sigma\">\n" +
            "\t\t<!ENTITY tau \"tau\">\n" +
            "\t\t<!ENTITY upsilon \"upsilon\">\n" +
            "\t\t<!ENTITY phi \"phi\">\n" +
            "\t\t<!ENTITY chi \"chi\">\n" +
            "\t\t<!ENTITY psi \"psi\">\n" +
            "\t\t<!ENTITY omega \"omega\">\n" +
            "\t\t<!ENTITY thetasym \"thetasym\">\n" +
            "\t\t<!ENTITY upsih \"upsih\">\n" +
            "\t\t<!ENTITY piv \"piv\">\n" +
            "\t\t<!ENTITY ensp \"ensp\">\n" +
            "\t\t<!ENTITY emsp \"emsp\">\n" +
            "\t\t<!ENTITY thinsp \"thinsp\">\n" +
            "\t\t<!ENTITY zwnj \"zwnj\">\n" +
            "\t\t<!ENTITY zwj \"zwj\">\n" +
            "\t\t<!ENTITY lrm \"lrm\">\n" +
            "\t\t<!ENTITY rlm \"rlm\">\n" +
            "\t\t<!ENTITY ndash \"ndash\">\n" +
            "\t\t<!ENTITY mdash \"mdash\">\n" +
            "\t\t<!ENTITY lsquo \"lsquo\">\n" +
            "\t\t<!ENTITY rsquo \"rsquo\">\n" +
            "\t\t<!ENTITY sbquo \"sbquo\">\n" +
            "\t\t<!ENTITY ldquo \"ldquo\">\n" +
            "\t\t<!ENTITY rdquo \"rdquo\">\n" +
            "\t\t<!ENTITY bdquo \"bdquo\">\n" +
            "\t\t<!ENTITY dagger \"dagger\">\n" +
            "\t\t<!ENTITY Dagger \"Dagger\">\n" +
            "\t\t<!ENTITY bull \"bull\">\n" +
            "\t\t<!ENTITY hellip \"hellip\">\n" +
            "\t\t<!ENTITY permil \"permil\">\n" +
            "\t\t<!ENTITY prime \"prime\">\n" +
            "\t\t<!ENTITY Prime \"Prime\">\n" +
            "\t\t<!ENTITY lsaquo \"lsaquo\">\n" +
            "\t\t<!ENTITY rsaquo \"rsaquo\">\n" +
            "\t\t<!ENTITY oline \"oline\">\n" +
            "\t\t<!ENTITY frasl \"frasl\">\n" +
            "\t\t<!ENTITY euro \"euro\">\n" +
            "\t\t<!ENTITY image \"image\">\n" +
            "\t\t<!ENTITY weierp \"weierp\">\n" +
            "\t\t<!ENTITY real \"real\">\n" +
            "\t\t<!ENTITY trade \"trade\">\n" +
            "\t\t<!ENTITY alefsym \"alefsym\">\n" +
            "\t\t<!ENTITY larr \"larr\">\n" +
            "\t\t<!ENTITY uarr \"uarr\">\n" +
            "\t\t<!ENTITY rarr \"rarr\">\n" +
            "\t\t<!ENTITY darr \"darr\">\n" +
            "\t\t<!ENTITY harr \"harr\">\n" +
            "\t\t<!ENTITY crarr \"crarr\">\n" +
            "\t\t<!ENTITY lArr \"lArr\">\n" +
            "\t\t<!ENTITY uArr \"uArr\">\n" +
            "\t\t<!ENTITY rArr \"rArr\">\n" +
            "\t\t<!ENTITY dArr \"dArr\">\n" +
            "\t\t<!ENTITY hArr \"hArr\">\n" +
            "\t\t<!ENTITY forall \"forall\">\n" +
            "\t\t<!ENTITY part \"part\">\n" +
            "\t\t<!ENTITY exist \"exist\">\n" +
            "\t\t<!ENTITY empty \"empty\">\n" +
            "\t\t<!ENTITY nabla \"nabla\">\n" +
            "\t\t<!ENTITY isin \"isin\">\n" +
            "\t\t<!ENTITY notin \"notin\">\n" +
            "\t\t<!ENTITY ni \"ni\">\n" +
            "\t\t<!ENTITY prod \"prod\">\n" +
            "\t\t<!ENTITY sum \"sum\">\n" +
            "\t\t<!ENTITY minus \"minus\">\n" +
            "\t\t<!ENTITY lowast \"lowast\">\n" +
            "\t\t<!ENTITY radic \"radic\">\n" +
            "\t\t<!ENTITY prop \"prop\">\n" +
            "\t\t<!ENTITY infin \"infin\">\n" +
            "\t\t<!ENTITY ang \"ang\">\n" +
            "\t\t<!ENTITY and \"and\">\n" +
            "\t\t<!ENTITY or \"or\">\n" +
            "\t\t<!ENTITY cap \"cap\">\n" +
            "\t\t<!ENTITY cup \"cup\">\n" +
            "\t\t<!ENTITY int \"int\">\n" +
            "\t\t<!ENTITY there4 \"there4\">\n" +
            "\t\t<!ENTITY sim \"sim\">\n" +
            "\t\t<!ENTITY cong \"cong\">\n" +
            "\t\t<!ENTITY asymp \"asymp\">\n" +
            "\t\t<!ENTITY ne \"ne\">\n" +
            "\t\t<!ENTITY equiv \"equiv\">\n" +
            "\t\t<!ENTITY le \"le\">\n" +
            "\t\t<!ENTITY ge \"ge\">\n" +
            "\t\t<!ENTITY sub \"sub\">\n" +
            "\t\t<!ENTITY sup \"sup\">\n" +
            "\t\t<!ENTITY nsub \"nsub\">\n" +
            "\t\t<!ENTITY sube \"sube\">\n" +
            "\t\t<!ENTITY supe \"supe\">\n" +
            "\t\t<!ENTITY oplus \"oplus\">\n" +
            "\t\t<!ENTITY otimes \"otimes\">\n" +
            "\t\t<!ENTITY perp \"perp\">\n" +
            "\t\t<!ENTITY sdot \"sdot\">\n" +
            "\t\t<!ENTITY lceil \"lceil\">\n" +
            "\t\t<!ENTITY rceil \"rceil\">\n" +
            "\t\t<!ENTITY lfloor \"lfloor\">\n" +
            "\t\t<!ENTITY rfloor \"rfloor\">\n" +
            "\t\t<!ENTITY lang \"lang\">\n" +
            "\t\t<!ENTITY rang \"rang\">\n" +
            "\t\t<!ENTITY loz \"loz\">\n" +
            "\t\t<!ENTITY spades \"spades\">\n" +
            "\t\t<!ENTITY clubs \"clubs\">\n" +
            "\t\t<!ENTITY hearts \"hearts\">\n" +
            "\t\t<!ENTITY diams \"diams\">\n" +
            "\t]>";

    /**
     * 在 XML 文件的第一行后插入指定的字符串，并保存到原文件。
     *
     * @param filePath  XML 文件的路径
     * @throws IOException 如果文件操作过程中发生错误
     */
    public static void insertStringAfterFirstLine(String filePath) throws IOException {
        String property = YamlUtils.getProperty("file.confPath") + File.separator + "DOCTYPE_HEAD.xml";

        if(FileUtil.exist(property)){
            DOCTYPE = FileUtil.readString(property, "UTF-16");
        }

        // 读取原文件内容，使用 UTF-16 编码
        String content = FileUtil.readString(filePath, "UTF-16");

        // 找到第一行并插入固定字符串
        int firstLineEnd = content.indexOf("\n");
        if (firstLineEnd == -1) {
            // 如果没有换行符，整个文件就是第一行
            firstLineEnd = content.length();
        }

        // 将固定字符串插入到第一行后
        String modifiedContent = content.substring(0, firstLineEnd) + "\n" + DOCTYPE + content.substring(firstLineEnd);

        // 将修改后的内容写回原文件，使用 UTF-16 编码
        FileUtil.writeString(modifiedContent, filePath, "UTF-16");

        System.out.println("XML 文件已修改并保存！");
    }

}
