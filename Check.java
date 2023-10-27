
import java.io.File;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Member;            // for Modifiers for Methods, Constructors and Fields
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;        // Executable is the shared superclass for the common functionality of Method and Constructor.
import java.lang.reflect.AccessibleObject;  // AccessibleObject is the shared superclass for the common functionality of Field, Method, and Constructor.
import java.lang.reflect.AnnotatedElement;  
import java.lang.reflect.InvocationTargetException;
import java.lang.annotation.Annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Repeatable;
import java.lang.annotation.RetentionPolicy;

import java.util.concurrent.FutureTask;         // A cancellable asynchronous computation... task.get(timeout, TimeUnit.MILLISECONDS);
import java.util.concurrent.TimeUnit;           // We use only milliseconds here
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// General lessons learned:
//    - An Object[] can not store primitives, it will store their wrappers!
//         => We can not distinguish bewteen e.g. Double and double in a parameter array without further hints!
//         => Such a hint could come from refSol.getParameterTypes() and/or stuSol.getParameterTypes()
//    - 


// ---------------------------------------- from RefSolClass ----------------------------------------------------


//  java.lang.reflect.Modifier.PUBLIC;       // 1
//  java.lang.reflect.Modifier.PRIVATE;      // 2
//  java.lang.reflect.Modifier.PROTECTED;    // 4
//  java.lang.reflect.Modifier.STATIC;       // 8

// see https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.6.1

@Retention(RetentionPolicy.RUNTIME)
@interface CheckIt {
}

@Retention(RetentionPolicy.RUNTIME)
@Deprecated
@interface MethodVariants {
    @Deprecated
	Class[] otherReturnTypes() default {}; 
            // an array of all the (in general) allowed return types for this method
            // NOTE: if for a certain reason a certain alternative type is problematic,
            //        then it must be "directly" tested in a separate test method (@Test), 
            //        example: int sumFromTo(int a, int b) may be ok but long is the better return type
            //          => use long as return type in the reference solution and list int in otherReturnTypes + use at least one test with a large result

    String[] modifierChecks() default {}; // e.g. {"public|protected:true", "static:false"} for an object method which must be public or protected    
}

@Retention(RetentionPolicy.RUNTIME)
@interface Variants {
	Class[] otherTypes() default {}; 
            // an array of all the (in general) allowed return types for this method
            // NOTE: if for a certain reason a certain alternative type is problematic,
            //        then it must be "directly" tested in a separate test method (@Test), 
            //        example: int sumFromTo(int a, int b) may be ok but long is the better return type
            //          => use long as return type in the reference solution and list int in otherReturnTypes + use at least one test with a large result

    String[] modifierChecks() default {}; // e.g. {"public|protected:true", "static:false"} for an object method which must be public or protected    
}

@Retention(RetentionPolicy.RUNTIME)
@interface TimeOutMs {
	int value() default 400; 
}

// @Retention(RetentionPolicy.RUNTIME)
// //@Repeatable(TestParams.class)    // see e.g. here??? https://www.javatpoint.com/java-8-type-annotations-and-repeating-annotations
                                    //                  https://www.javabrahman.com/java-8/java-8-repeating-annotations-tutorial/
                                    //                  https://dzone.com/articles/repeatable-annotations-in-java-8-1
// @interface TestParams {
//    String[] chkIds() default {};   // ids of the checks where these test parameters shall be applied 
//    String[] values() default {};
// 	  String[] ranges() default {}; 
// }

// TestParamSet
//  - There can be different sets of test parameters -> each set can be applied to certain scopes
//  - Values: A list of certain parameter lists which are to be tested
//  - Ranges: A list of parameter lists where each single parameter is described by a range.
//            An optional number per parameter list defines the number of random tests with this paramter list.
//  - Fields: A list of "parameter lists" for field initialisation before calling a method.
//            The first String is a name list showing the used fields and their order.
//            Further parameters will be interpreted as values for the parameter list.
// For java examples see e.g.:
//      https://docs.oracle.com/javase/tutorial/java/annotations/repeating.html
//      https://www.javatpoint.com/java-8-type-annotations-and-repeating-annotations
//      https://www.javabrahman.com/java-8/java-8-repeating-annotations-tutorial/
//      https://dzone.com/articles/repeatable-annotations-in-java-8-1
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(TestParamSets.class)
@interface TestParamSet {
    String[] scopes() default {};   // IDs of the scopes where the parameters can be applied
	String[] values() default {};
	String[] ranges() default {}; 
	String[] fields() default {}; 
}
@Retention(RetentionPolicy.RUNTIME)
@interface TestParamSets {
    TestParamSet[] value();
}
//////////////////////////////////////////////


@Retention(RetentionPolicy.RUNTIME)
@interface TestParamsList {
}

class RefSolClass {  // base class for all our reference solutions 
                     // aims: 1. can check in "common" code if the testees solution is derived 
                     //           from this class -> not allowed!

}

// ---------------------------------------- from ModifierCheck ----------------------------------------------------

class ModifierCheck {
    int mask;
    boolean isNot0;
    String def;
    
    public static Pattern pat_modif  = Pattern.compile("(public|protected|private|static)");
    public static Pattern pat_result = Pattern.compile("(true|false)");
    
    public ModifierCheck(int m, boolean v, String s){
        this.mask   = m;
        this.isNot0 = v;
        this.def    = s;
    }
    
    public boolean ok(int modif){
        return ((modif & mask) != 0) == isNot0;
    }
    
    public String shouldBeDe(){
        return (isNot0 ? "" : "nicht ")+toStr(mask, "oder");
    }
    
    public String isDe(int modif){
        return toStr(modif&expand(mask), "und");
    }
    
    public static String isDeStatic(int m, int o){
        
        int x=m^o; // mask: the bits which differ
        String sep=" und ";
        
        String s = "";
        if ((  x&java.lang.reflect.Modifier.STATIC   ) != 0) s = ((m&java.lang.reflect.Modifier.STATIC   )==0 ? "nicht ": "") + "static";
        
        String v = "";
        if ((m&x&java.lang.reflect.Modifier.PUBLIC   ) != 0) v = ((m&java.lang.reflect.Modifier.PUBLIC   )==0 ? "nicht ": "") + "public";    
        if ((m&x&java.lang.reflect.Modifier.PRIVATE  ) != 0) v = ((m&java.lang.reflect.Modifier.PRIVATE  )==0 ? "nicht ": "") + "private";    
        if ((m&x&java.lang.reflect.Modifier.PROTECTED) != 0) v = ((m&java.lang.reflect.Modifier.PROTECTED)==0 ? "nicht ": "") + "protected";    
        
        return v+(v.length()>0 && s.length()>0 ? sep : "")+s;    
    }
    
    public static String isDeStatic(int m){
        String sep=" und ";
        String s=((m&java.lang.reflect.Modifier.STATIC   ) == 0) ? "nicht static" : "static";
        String r="";
        if ((m&java.lang.reflect.Modifier.PUBLIC   ) != 0) r+=(r.length()==0 ? "":sep) + "public";    
        if ((m&java.lang.reflect.Modifier.PRIVATE  ) != 0) r+=(r.length()==0 ? "":sep) + "private";    
        if ((m&java.lang.reflect.Modifier.PROTECTED) != 0) r+=(r.length()==0 ? "":sep) + "protected";    
        if (r.length()==0)
            return s+sep+"nicht public, nicht private und auch nicht protected";
        return r+sep+s;    
    }
    
    public static int expand(int m){
        int vis = java.lang.reflect.Modifier.PUBLIC   
                | java.lang.reflect.Modifier.PRIVATE  
                | java.lang.reflect.Modifier.PROTECTED;
        
        return m | ((m&vis)==0 ? 0 : vis); 
    }
    
    public static String toStr(int m){
        return toStr(m, " ");
    }
    public static String toStr(int m, String sep){
        String r="";
        if ((m&java.lang.reflect.Modifier.PUBLIC   ) != 0) r+=(r.length()==0 ? "":sep) + "public";    
        if ((m&java.lang.reflect.Modifier.PRIVATE  ) != 0) r+=(r.length()==0 ? "":sep) + "private";    
        if ((m&java.lang.reflect.Modifier.PROTECTED) != 0) r+=(r.length()==0 ? "":sep) + "protected";    
        if ((m&java.lang.reflect.Modifier.STATIC   ) != 0) r+=(r.length()==0 ? "":sep) + "static";    
        return r;
    }

    public static ModifierCheck[] parse(String[] mChkStrArr){
        int numOk=0;
        ModifierCheck[] mca = new ModifierCheck[mChkStrArr.length];
        for (String mChkStr:mChkStrArr){
            String[] parts = mChkStr.split(":");
            if (parts.length!=2){
                sopl("ERROR, there is no : in the ModifierCheck definition "+mChkStr);
                continue;
            }
            
            int mask=0;
            boolean mOk=true;
            for (String s:parts[0].split("\\|")){
                Matcher m = pat_modif.matcher(s.trim().toLowerCase());
                if (!m.matches()){
                    sopl("ERROR, unknown modifier "+s+" found in the ModifierCheck definition "+mChkStr);
                    mOk = false;
                    break;
                }
                switch (m.group(1)){    // see e.g. https://docs.oracle.com/javase/8/docs/api/constant-values.html#java.lang.reflect.Modifier.PUBLIC etc
                    case "public"    : mask |= java.lang.reflect.Modifier.PUBLIC   ; break; // 1
                    case "private"   : mask |= java.lang.reflect.Modifier.PRIVATE  ; break; // 2
                    case "protected" : mask |= java.lang.reflect.Modifier.PROTECTED; break; // 4
                    case "static"    : mask |= java.lang.reflect.Modifier.STATIC   ; break; // 8
                }
            }
            if (!mOk)
                continue;
            
            Matcher m = pat_result.matcher(parts[1].trim().toLowerCase());
            if (!m.matches()){
                sopl("ERROR, unknown result "+parts[1]+" found in the ModifierCheck definition "+mChkStr+" (should be true or false)");
                continue;
            }
            
            mca[numOk++] = new ModifierCheck( mask, m.group(1).equals("true"), mChkStr);
        }
        
        ModifierCheck[] ret = new ModifierCheck[numOk];
        for (int i=0; i<numOk; i++)
            ret[i] = mca[i];
        return ret;
    }
    
    public String toString(){
        return "mask:"+mask+" isNot0:"+isNot0+" def:"+def;
    }

    public static void sopl(String s){
        System.out.println(s);
    }

    public static void main(String[] a){
        String[]       defs1 = {"public|protected:true", "static:false"}; //  for an object method which must be public or protected   
        ModifierCheck[] mca1 = parse(defs1);
        for (ModifierCheck mc : mca1)
            sopl(""+mc);
    }
}


// ---------------------------------------- from Range ----------------------------------------------------

class Range {
    abstract class R {  // this is the base for all typed range data where we will store the typed begin and end values (2 values per range), see e.g. classes Rb or RB 
        abstract Object v();    // this will return a random value from the range (equal probability for all values)
        abstract boolean isValue();
    }
    
    public static int defNumPerRange = 7;  // usually there is a number how many (parameter) values are used per range, if not we use defNumPerRange values

    // random values in the range b..e
    public static byte    r(byte    b, byte    e){ return (byte  )(b+(byte  )((e-b+1)*Math.random())); }
    public static short   r(short   b, short   e){ return (short )(b+(short )((e-b+1)*Math.random())); }
    public static int     r(int     b, int     e){ return (int   )(b+(int   )((e-b+1)*Math.random())); }
    public static long    r(long    b, long    e){ return (long  )(b+(long  )((e-b+1)*Math.random())); }
    public static float   r(float   b, float   e){ return (float )(b+(float )((e-b  )*Math.random())); }
    public static double  r(double  b, double  e){ return (double)(b+(double)((e-b  )*Math.random())); }
    public static char    r(char    b, char    e){ return (char  )(b+(char  )((e-b+1)*Math.random())); }
    public static boolean r(boolean b, boolean e){ return   b==e ? b         : (0.5 < Math.random() ); } 

    // rounding to a certain number of digits for floating point values
    public static float  r2(float  b, float  e){ return round(r(b, e), 2); }
    public static double r2(double b, double e){ return round(r(b, e), 2); }
    public static float  rn(float  b, float  e, int n){ return round(r(b, e), n); }
    public static double rn(double b, double e, int n){ return round(r(b, e), n); }
    public static float  round(float  v, int n){ float  f=(float)Math.pow(10, n); return ((long)(v*f+(v<0?-.5:.5)))/f; }
    public static double round(double v, int n){ double f=       Math.pow(10, n); return ((long)(v*f+(v<0?-.5:.5)))/f; }
    
    // parsing given Strings to the supported primitive types
    public static byte    pB(String s) { return Byte   .parseByte  (s.replaceAll("_", "")); }
    public static short   pS(String s) { return Short  .parseShort (s.replaceAll("_", "")); }
    public static int     pI(String s) { return Integer.parseInt   (s.replaceAll("_", "")); }
    public static long    pL(String s) { return Long   .parseLong  (s.replaceAll("_", "")); }
    public static float   pF(String s) { return Float  .parseFloat (s                    ); }
    public static double  pD(String s) { return Double .parseDouble(s                    ); }
    public static char    pC(String s) { return                     s.charAt(0)           ; }
    public static boolean pT(String s) { // s=s.toLowerCase(); // note: Boolean.parseBoolean does not fit our needs
                                         if (s.equals("true" )) return true;
                                         if (s.equals("false")) return false; 
                                         throw new NumberFormatException(); } // ok, in fact it is no number, but can be seen as 0 vs 1;-)
    
    // parsers for wrapped primitive values -> null is supported here; BUT: NOTE that null is not supported in our "low level" ranges!!!
    public static Byte      p_B(String s) { return s==null || s.equals("null") ? null : pB(s); }
    public static Short     p_S(String s) { return s==null || s.equals("null") ? null : pS(s); } 
    public static Integer   p_I(String s) { return s==null || s.equals("null") ? null : pI(s); }
    public static Long      p_L(String s) { return s==null || s.equals("null") ? null : pL(s); } 
    public static Float     p_F(String s) { return s==null || s.equals("null") ? null : pF(s); } 
    public static Double    p_D(String s) { return s==null || s.equals("null") ? null : pD(s); } 
    public static Character p_C(String s) { return s==null || s.equals("null") ? null : pC(s); } 
    public static Boolean   p_T(String s) { return s==null || s.equals("null") ? null : pT(s); }  
    
    // constructors for ranges of primitive values; ensuring that b<=e is especially important for calculating random of integer values or char values; floats only theoretically...
    class Rb extends R{ byte    b,e;   Rb(String x, String y){ b=pB(x); e=pB(y); if(e<b){byte    h=e;e=b;b=h;}}   Object v(){ return r (b,e); } boolean isValue(){return b==e;}}
    class Rs extends R{ short   b,e;   Rs(String x, String y){ b=pS(x); e=pS(y); if(e<b){short   h=e;e=b;b=h;}}   Object v(){ return r (b,e); } boolean isValue(){return b==e;}}
    class Ri extends R{ int     b,e;   Ri(String x, String y){ b=pI(x); e=pI(y); if(e<b){int     h=e;e=b;b=h;}}   Object v(){ return r (b,e); } boolean isValue(){return b==e;}}
    class Rl extends R{ long    b,e;   Rl(String x, String y){ b=pL(x); e=pL(y); if(e<b){long    h=e;e=b;b=h;}}   Object v(){ return r (b,e); } boolean isValue(){return b==e;}}
    class Rf extends R{ float   b,e;   Rf(String x, String y){ b=pF(x); e=pF(y); if(e<b){float   h=e;e=b;b=h;}}   Object v(){ return r2(b,e); } boolean isValue(){return b==e;}}
    class Rd extends R{ double  b,e;   Rd(String x, String y){ b=pD(x); e=pD(y); if(e<b){double  h=e;e=b;b=h;}}   Object v(){ return r2(b,e); } boolean isValue(){return b==e;}}
    class Rc extends R{ char    b,e;   Rc(String x, String y){ b=pC(x); e=pC(y); if(e<b){char    h=e;e=b;b=h;}}   Object v(){ return r (b,e); } boolean isValue(){return b==e;}}
    class Rt extends R{ boolean b,e;   Rt(String x, String y){ b=pT(x); e=pT(y);                              }   Object v(){ return r (b,e); } boolean isValue(){return b==e;}}

    // constructors for ranges of wrapped primitive values; ensuring that b<=e is especially important for calculating random of integer values or char values; floats only theoretically...
    class RB extends R{ Byte      b,e;   RB(String x, String y){ b=pB(x); e=pB(y); if(e<b){byte    h=e;e=b;b=h;}}   Object v(){ return Byte     .valueOf(r (b,e)); } boolean isValue(){return b==e;}}
    class RS extends R{ Short     b,e;   RS(String x, String y){ b=pS(x); e=pS(y); if(e<b){short   h=e;e=b;b=h;}}   Object v(){ return Short    .valueOf(r (b,e)); } boolean isValue(){return b==e;}}
    class RI extends R{ Integer   b,e;   RI(String x, String y){ b=pI(x); e=pI(y); if(e<b){int     h=e;e=b;b=h;}}   Object v(){ return Integer  .valueOf(r (b,e)); } boolean isValue(){return b==e;}}
    class RL extends R{ Long      b,e;   RL(String x, String y){ b=pL(x); e=pL(y); if(e<b){long    h=e;e=b;b=h;}}   Object v(){ return Long     .valueOf(r (b,e)); } boolean isValue(){return b==e;}}
    class RF extends R{ Float     b,e;   RF(String x, String y){ b=pF(x); e=pF(y); if(e<b){float   h=e;e=b;b=h;}}   Object v(){ return Float    .valueOf(r2(b,e)); } boolean isValue(){return b==e;}}
    class RD extends R{ Double    b,e;   RD(String x, String y){ b=pD(x); e=pD(y); if(e<b){double  h=e;e=b;b=h;}}   Object v(){ return Double   .valueOf(r2(b,e)); } boolean isValue(){return b==e;}}
    class RC extends R{ Character b,e;   RC(String x, String y){ b=pC(x); e=pC(y); if(e<b){char    h=e;e=b;b=h;}}   Object v(){ return Character.valueOf(r (b,e)); } boolean isValue(){return b==e;}}
    class RT extends R{ Boolean   b,e;   RT(String x, String y){ b=pT(x); e=pT(y);                              }   Object v(){ return Boolean  .valueOf(r (b,e)); } boolean isValue(){return b==e;}}

    Class  typ; // type of the value for which we define this Range 
    String def; // the definition text we have parsed to initialize this Range
    String err; // here we store an error hint, if some was found during parsing
    R range;    // stores the begin and end value and can return typed random values via method v()
    int num;    // the number of parameters we shall generate for this range in the tests
    
    public static String  patSt_cnt  = "([_0-9]+)";
    public static String  patSt_int  = "([-+]?[_0-9]+)";
    public static String  patSt_val  = "([-+]?[_0-9]+(?:\\.[0-9])?|'(.)')"; 
    public static String  patSt_valR =           patSt_val + "\\s*\\.\\.\\.\\s*" + patSt_val;           //  -17...42.3   -> general numbers, no 1e-10 (todo???)
    public static String  patSt_lenR = "\\[\\s*"+patSt_int + "\\s*\\.\\.\\.\\s*" + patSt_int+"\\s*\\]"; // [ -1...17 ]   -> integers only (neg. allowd, e.g. for -1 == null)
    public static String  patSt_cntV = "(?:"+patSt_cnt+"\\s*:|)";                                       //  3:           -> an optional count value (no neg. values!)

//    public static Pattern pat_rangePri  = Pattern.compile(patSt_cntV +"\\s*"+ patSt_priR);          // "n:v0...ve" or "v0..ve" for number or char values
    
    // note: To support n-dimensional arrays we have to take into account that working with groups is not trivial if the number of groups is not clear
    //       Here we could try to use quantifiers like * or + for patSt_lenR, BUT we would only get the begin and end of the last range!!!
    //       See tests in main-method near the comment with the URL https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html#groupname
    //       => Therefore we use non-capturing versions to get all array size ranges (1 per dimension) at once. 
    public static String patSt_someInt  = "(?:[-+]?[_0-9]+)";                       // same as patSt_int (an optionally signed integer number) but as a non-capturing group
    public static String patSt_arrLenR  = "\\[\\s*"+patSt_someInt+"\\s*\\.\\.\\.\\s*"+patSt_someInt+"\\s*\\]";  // similar to patSt_lenR but non-capturing
    public static String patSt_nArrLenR = "((?:"+patSt_arrLenR+")*)";
    public static Pattern pat_rangeValA = Pattern.compile(patSt_cntV +"\\s*"+ patSt_nArrLenR +"\\s*"+ patSt_valR);  // "n:[-1...10]v0...ve" or "[-1...10]v0..ve" for prim. arrays
                                                                                      // or more than 1 dimesion, e.g. "n:[-1...10][-1..3]v0...ve" or "[-1...2][0..7]v0..ve"
    public static Pattern pat_rangeLen  = Pattern.compile("("+patSt_lenR+")"); // matches a single length range

    // TODO: - may be we shold find a way to have ranges for the first array elements (1st 2nd 3rd) 
    //         BUT: note that separation by comma may collide with camma in param lists!
    //         may be we should put array element panges into curly brakets
    //         -> would also allow to unify values() and ranges() ???

    class A  extends R { // data for an array definition with ranges for number of elements and element values
        Class<?> eTy;
        Ri nr;  // int range for number of elements
        R  er;  // typed range for element values (note: for arrays with more than 1 dimension this may be an array too!) 
        
        A (String nb, String ne, Class<?> elemType, R elemRange){
            eTy= elemType;
            nr = new Ri(nb, ne); 
            er = elemRange;
        }  
        
        int n(){ 
            return r(nr.b, nr.e); 
        } 
        
        Object v(){ 
            int len=n();
            if (len<0)
                return null;
            
            Object ret = Array.newInstance(eTy, len); 
            for (int i=0; i<len; i++)
                Array.set(ret, i, er.v());
                
            return ret; 
        } 
        
        boolean isValue(){
            return nr.isValue() && er.isValue();  // array length must be a fix value as well as each element (implies that all have the same value:-()
        }
    }
    
    boolean isValue(){
        return range.isValue();  // our wrapped "primitive" range must be fixed
    }

    public R fromType(Class typ, String a, String b, String e){
        if (typ.isArray()){
            Matcher m = pat_rangeLen.matcher(a);
            if (!m.find()){
                sopl("RANGE typ:"+typ+" does not matche range length definition="+a);
                return null;
            }
            Class<?> eTy = typ.getComponentType();
            return new A(m.group(2), m.group(3), eTy, fromType(eTy, a.substring(m.end(1)), b, e));
        }    
        else if (typ == byte       .class) return new Rb(b, e);
        else if (typ == short      .class) return new Rs(b, e);
        else if (typ == int        .class) return new Ri(b, e);
        else if (typ == long       .class) return new Rl(b, e);
        else if (typ == float      .class) return new Rf(b, e);
        else if (typ == double     .class) return new Rd(b, e);
        else if (typ == char       .class) return new Rc(b, e);
        else if (typ == boolean    .class) return new Rt(b, e);
        else if (typ == Byte       .class) return new RB(b, e);
        else if (typ == Short      .class) return new RS(b, e);
        else if (typ == Integer    .class) return new RI(b, e);
        else if (typ == Long       .class) return new RL(b, e);
        else if (typ == Float      .class) return new RF(b, e);
        else if (typ == Double     .class) return new RD(b, e);
        else if (typ == Character  .class) return new RC(b, e);
        else if (typ == Boolean    .class) return new RT(b, e);
        else {
            sopl("UNKNOWN RANGE typ:"+typ+", def="+def);
            return null;
        }
    }
    
    public Range(Class typ, String def){
        this.typ = typ;
        this.def = def;
        Matcher m = pat_rangeValA.matcher(def); // the array matcher also accepts "no array"-info => group 2 will be null
        // sopl("RangeConstructor("+typ+", "+def+") -> "+m);
        if (m.matches()){
            String n  = m.group(1);
            String a  = m.group(2); // specal array info: the range length for all the dimensions, may be null
            String b  = m.group(m.group(4)==null ? 3 : 4);  // example: "'v'" has grp3=="'v'" and grp4="v" BUT "1" has grp3=="1" and grp4=null
            String e  = m.group(m.group(6)==null ? 5 : 6);  //          similar for the range end
            
            this.num = n==null ? defNumPerRange : pI(n);
            try {
                this.range = fromType(typ, a, b, e);
                // NOTE: the inner range may stay null if fromType can not handle typ -> the allAreValid() method should detect this
            } catch (Exception x){
                this.err = "BAD RANGE for typ:"+typ+", def="+def+" exept:"+x;
                sopl(err);
            }
        }
        else {
                this.err = "BAD RANGE Pattern for typ:"+typ+", def="+def;
                sopl(err);
        }
    }

    Object getSomeValue(){
        return range==null ? null : range.v();
    }
    
    String getSomeValueAsString(Check chk){
        return chk==null ? Check.asStringStatic(getSomeValue())
                         : chk.asString(getSomeValue());
    }
    
    public static Range[] rangeArrFromStr(Class[] paT, String rangesStr){
        Range[]  ra  = new Range[paT.length];
        String[] rsa = rangesStr.split(",");
        for (int i=0; i<ra.length && i<rsa.length; i++)
            ra[i] = new Range(paT[i], rsa[i].trim());
        return ra;
    }

    public static void checkRangeArray(Class[] tyArr, Range[] a){
        if (a==null)
            throw new NumberFormatException("ERROR in checkRangeArray() : array of ranges does not exist!");
        if (tyArr.length!=a.length)
            throw new NumberFormatException("ERROR in checkRangeArray() : array has length="+a.length+" but should have length="+tyArr.length);
        int i=0;
        for (Range r:a){
            if (r==null)   
                throw new NumberFormatException("ERROR in checkRangeArray() at index "+i+": does not exist");
            if (r.err!=null)
                throw new NumberFormatException("ERROR in checkRangeArray() at index "+i+": "+r.err);
            if (r.range==null)  
                throw new NumberFormatException("ERROR in checkRangeArray() at index "+i+": has no inner range R with typed begin and end value");
            i++;
        }
    }

    public static boolean allAreValid(Range[] a){   // weak test, generates only the info: ok or not
        if (a==null)
            return false;
        for (Range r:a)
            if (r==null || r.range==null)   // note: the "abstract" range r must have a certain type specific inner range r.range!!!
                return false;
        return true;
    }

    public static String toParamsStr(Range[] a, Check chk){
        if (a==null)
            return "";
        String s="";
        for (int i=0; i<a.length; i++)
            if (a[i]!=null)
                s+=(i==0?"":", ")+a[i].getSomeValueAsString(chk);
        return s;
    }

    public String dbgStr(){
        return dbgStr((Check)null);
    }
    
    public String dbgStr(Check chk){
        return "t:"+(""+typ).replaceAll("class java.lang.", "")+"  d:"+def+"  n:"+num+"  v:"+getSomeValueAsString(chk);
    }

    public static String dbgStr(Range[] a){
        return dbgStr(a, null);
    }
    
    public static String dbgStr(Range[] a, Check chk){
        String ret="[";
        for (Range r:a)
            ret += r==null ? null : " {"+r.dbgStr(chk)+"} ";
        return ret+"]";
    }

    public static void sopl(String s){
        System.out.println(s);
    }

    public static void main(String[] a){
        Range rB = new Range(byte  .class,   "5...127" ); sopl("rB : "+rB.dbgStr());
        Range rS = new Range(short .class,   "5...12"  ); sopl("rS : "+rS.dbgStr());
        Range rI = new Range(int   .class,  "-5...12"  ); sopl("rI : "+rI.dbgStr());
        Range rL = new Range(long  .class,  "-1...+1"  ); sopl("rL : "+rL.dbgStr());
        Range rF = new Range(float .class,   "5...12"  ); sopl("rF : "+rF.dbgStr());
        Range rD = new Range(double.class,   "5...12"  ); sopl("rD : "+rD.dbgStr());
        Range rC = new Range(char  .class, "'a'...'e'" ); sopl("rC : "+rC.dbgStr());
        
        Range rBn = new Range(byte  .class,   "3:5...127" ); sopl("rBn : "+rBn.dbgStr());
        Range rSn = new Range(short .class,  "17:5...12"  ); sopl("rSn : "+rSn.dbgStr());
        Range rIn = new Range(int   .class,  "2:-5...12"  ); sopl("rIn : "+rIn.dbgStr());
        Range rLn = new Range(long  .class,  "1:-1...+1"  ); sopl("rLn : "+rLn.dbgStr());
        Range rFn = new Range(float .class,   "9:5...12"  ); sopl("rFn : "+rFn.dbgStr());
        Range rDn = new Range(double.class,  "99:5...12"  ); sopl("rDn : "+rDn.dbgStr());
        Range rCn = new Range(char  .class, "3:'a'...'e'" ); sopl("rCn : "+rCn.dbgStr());
        
        Object[] oa = { 1, Integer.valueOf(2) };
        for (Object o:oa)
            sopl(""+o.getClass()+" -> "+o);

        sopl(""+int.class);
        sopl(""+Integer.class);
    }
}

// --------------- from CancellableExecution.java (and other) -----------------------------------------------------------------------------------------------

interface Code {
    public void execNow() throws Throwable;
    public String getHint();
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

class TimedOutException extends RuntimeException {

    private final long timeoutMs;

    public TimedOutException(long toutMs, Thread t) {
        super("Forced timeout after "+toutMs+"ms");
        timeoutMs = toutMs;
        setStackTrace(getStackTrace(t));  // provide the stacktrace -> what is it good for? -> Hint generation!
    }

    public long getTimeoutInMs() {
        return timeoutMs;
    }
    
    // Simple helper method to handle null + ignore SecurityExceptions during Thread.getStackTrace()
    public static StackTraceElement[] getStackTrace(Thread t) {
        if (t!=null)
            try{ StackTraceElement[] x = t.getStackTrace(); 
                 if (x!=null)
                    return x;
                } 
            catch (SecurityException e){}
        return new StackTraceElement[0]; // no thread -> empty stack trace
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

class CancellableExecution {
    protected static long serNo = 0;
    
    protected       Code theCode;
    protected final long timeout;
    protected final long timeoutMax = 1000*10; // e.g. 10 seconds
    protected final long timeoutMin =      1 ; // e.g. 1 milliseconds
    protected       long nDoExec = 0;
    public       boolean locDbg = false;
    public Throwable catchedThrowable;
    public RuntimeException catchedRuntimeException;

    protected CancellableExecution(long tout, Code code) {
        serNo++;
        theCode = code;
        timeout = Math.min(timeoutMax, Math.max(timeoutMin, tout));
    }

    private class MyCodeWrapper implements Callable<Throwable> {
        // https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/Callable.html#call()
        public Throwable call() throws Exception {  // Computes a result, or throws an exception if unable to do so.
            try { theCode.execNow(); }
            catch (Exception e) { throw  e; }
            catch (Throwable e) { return e; }
            return null;
        }
    }
    
    public void doExec() {
        doExec(locDbg);
    }
    
    public void doExec(boolean dbg) {
        if (dbg)
            System.out.println("\n---------------------------------------------------------------------------------\n");
        nDoExec++;
        ThreadGroup threadGrp = new ThreadGroup("TGrp_CancellableExecution_"+serNo+"_"+nDoExec);
//                    threadGrp.setDaemon(true);
        FutureTask<Throwable> task = new FutureTask<Throwable>(new MyCodeWrapper());
        
        // Make a daemon thread, a user thread will not stop on timeout!
        Thread thread = new Thread(threadGrp, task, "ThreadCancellableExecution_"+serNo+"_"+nDoExec);
        thread.setDaemon(true); // https://docs.oracle.com/javase/7/docs/api/java/lang/Thread.html#setDaemon(boolean) -> daemon vs. user
        thread.start();         // setDaemon() must be before start()!!   NOTE: will call the method MyCodeWrapper.call() (see above, which calls theCode.execNow())
        
        catchedThrowable = getResult(task, thread);
        if (catchedThrowable instanceof InvocationTargetException && ((InvocationTargetException)catchedThrowable).getTargetException()!=null)
            catchedThrowable = ((InvocationTargetException)catchedThrowable).getTargetException();
        if (catchedThrowable instanceof RuntimeException)
            catchedRuntimeException = (RuntimeException)catchedThrowable;
        if (dbg){
            if (catchedThrowable != null)
                System.out.println("doExec["+theCode.getHint()+"] catched: " + catchedThrowable+" --> is RuntimeException?: "+(catchedRuntimeException!=null));
            else
                System.out.println("doExec["+theCode.getHint()+"] catched nothing -------------------------------------------------");
        }
        thread.setPriority(Thread.MIN_PRIORITY);
        if (dbg)
            threadGrp.list();
        
        // For timeouts we get true calling task.cancel(true), else false, see
        // System.out.println("######### "+theCode.getHint()+" ####"+task.cancel(true)); // https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/FutureTask.html#cancel(boolean)
        // but nevertheless the thread will not stop!
        
        // following was not necessary under openjdk version "17.0.6" 2023-01-17
        //   -> hopefully destroy() is not needed as it seemed to be in former times...
        //      2023 with the jdk version shown above computing time for the thread changed from 100% to 0 (thread was killed, i.e. its pid was not in list of ps aux)
        try {
            // threadGrp.destroy();  // warning: [removal] destroy() in ThreadGroup has been deprecated and marked for removal
        } catch (IllegalThreadStateException e){
            System.out.println("#### DESTROY ##### "+theCode.getHint()+" ####"+e);
        }
    }

    private Throwable getResult(FutureTask<Throwable> task, Thread thread) {
        try                            { return task.get(timeout, TimeUnit.MILLISECONDS); } // Ok, we had no exec problems
        catch (InterruptedException e) { return e;                                        } // if the current thread was interrupted while waiting 
        catch (ExecutionException   e) { return e.getCause();                             } // if the computation threw an exception, we are only interested in the reason -> return it
        catch (TimeoutException     e) { return new TimedOutException(timeout, thread);   } // if the wait timed out -> that is what we are interested in... :-)
        finally                        { if (locDbg) System.out.println("CancellableExecution is finished now by calling thread.interrupt() for "+theCode.getHint());
                                         thread.interrupt();                              }
    }

//     // Simple test and/or execution examples
//     public static void t1(){
//         System.out.println("CancellableExecution.t1(): Starting to test errors for CancellableExecution");
//         
//         boolean x = true;
//         int toms = 100;
//         new CancellableExecution( 5   , new CodeErrGen(CodeErrGen.Error.NoError             )).doExec(x); // todo: why does it run soooo long ???
// 
//         new CancellableExecution( toms, new CodeErrGen(CodeErrGen.Error.BadArrayIdx_tooBig  )).doExec(x);
//         new CancellableExecution( toms, new CodeErrGen(CodeErrGen.Error.BadArrayIdx_tooLow  )).doExec(x);
//         new CancellableExecution( toms, new CodeErrGen(CodeErrGen.Error.ArithmExept_divBy0  )).doExec(x);
//         new CancellableExecution( toms, new CodeErrGen(CodeErrGen.Error.StackOverflow       )).doExec(x);
//         new CancellableExecution( toms, new CodeErrGen(CodeErrGen.Error.TimeOut             )).doExec(x);
//         new CancellableExecution( toms, new CodeErrGen(CodeErrGen.Error.AssertionError      )).doExec(x);
//         new CancellableExecution( toms, new CodeErrGen(CodeErrGen.Error.NullPointerAccess   )).doExec(x);
//         new CancellableExecution( toms, new CodeErrGen(CodeErrGen.Error.NegArraySize        )).doExec(x);
//         new CancellableExecution( toms, new CodeErrGen(CodeErrGen.Error.OutOfMemo_heapSpace )).doExec(x); // may have to use memory via: java -Xmx1000m 
//         new CancellableExecution( toms, new CodeErrGen(CodeErrGen.Error.OutOfMemo_sizeVMLim )).doExec(x);
//         new CancellableExecution( toms, new CodeErrGen(CodeErrGen.Error.NoClassDefFound     )).doExec(x);
//         new CancellableExecution( toms, new CodeErrGen(CodeErrGen.Error.BadStringIdx_tooBig )).doExec(x);
//         new CancellableExecution( toms, new CodeErrGen(CodeErrGen.Error.BadStringIdx_tooLow )).doExec(x);
// 
// //        new CancellableExecution( toms, new CodeErrGen(CodeErrGen.Error.ForbiddenClassUsage )).doExec(x);
// 
//         new CancellableExecution( 5   , new CodeErrGen(CodeErrGen.Error.NoError             )).doExec(x); // todo: why does it stop so fast???
//         System.out.println("CancellableExecution.t1(): Finished to test errors for CancellableExecution");
//     }

}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

class MethodCallWrapper extends CancellableExecution implements Code {
    Check theCheck;
    Executable exec;
    Object     thisObj; 
    Object[]   params;
    Object     returnValue;
    boolean execIsStarted = false;
    boolean execNormalEnd = false;
    boolean locDbg;
    
    public MethodCallWrapper(Executable x, Object o, Object[] p, boolean dbg, long timeOutInMs, Check chk){
        super(timeOutInMs, null);
        exec=x; thisObj=o; params=p; locDbg=dbg;
        theCode = this;
        theCheck = chk;
    }
    
    public void execNow() throws Throwable {
//        try { 
            execIsStarted = true;
            returnValue   = (exec instanceof Method) ? ((Method     )exec).invoke(thisObj, params)
                                                     : ((Constructor)exec).newInstance    (params);
            execNormalEnd = true;
//        } catch (Throwable e){ 
//            if (locDbg){
//                Check.sopl(getHint()+"\n   -> catched "+e+"\n   -> e.getCause() is :"+e.getCause()+"\n");
//                // if (e.getCause()!=null)
//                //     e.getCause().printStackTrace();
//            }
//            throw(e); // re-throw the exception now 
//        }
    }
    
    public String toString(){
        return getHint();
    }
    
    public String getHint(){
        return "MethodCallWrapper("+Check.calcSignature(exec, true)+", "+thisObj+", "+theCheck.asString(params)+") execIsStarted:"+execIsStarted+" execNormalEnd:"+execNormalEnd;
    }
    
    public Object doExecMethod(){
        doExec();
        if (catchedRuntimeException!=null)
            throw catchedRuntimeException;
        return returnValue;
    }
}

// --------------------------------------------------------------------------------------------------------------

class RTFunc {

    Process p = null;
    String result = "";
    
    static public String exec(String cmd)
    {
        return exec(cmd, false, false);
    }

    static public String exec(String cmd, boolean inShell)
    {
        return exec(cmd, inShell, false);
    }

    static public String exec(String cmd, boolean inShell, boolean dbg){
        return new RTFunc().execP(cmd, inShell, dbg);
    }

    public String execP(String cmd, boolean inShell, boolean dbg)
    {
        if (dbg)
            System.out.println(cmd);

        String ret = "";
        try { 
            p = null;
            if (inShell){
                String   sh  = "/bin/sh";
                String   cop = "-c";
                String[] cis = new String[] {sh, cop, cmd};
                p = Runtime.getRuntime().exec(cis); 
            }
            else 
                p = Runtime.getRuntime().exec(new String[] {cmd}); // fixes warning: [deprecation] exec(String) in Runtime...   p = Runtime.getRuntime().exec(cmd);
            
            BufferedReader inE = new BufferedReader( new InputStreamReader(p.getErrorStream()) );
            BufferedReader inO = new BufferedReader( new InputStreamReader(p.getInputStream()) );
            // p.waitFor();
            
            for (;;){
                String sE = inE.readLine(); if (sE!=null) ret+=sE+"\n";
                String sO = inO.readLine(); if (sO!=null) ret+=sO+"\n";
                if (sE==null && sO==null)
                    break;
            }
        }
        catch (Exception e) { ret = "Problem calling: "+cmd+
                                    "\n until here we got :"+ ret; }
        p=null;
        
        if (dbg)
            System.out.println("FINISHED "+cmd);
            
        return ret;
    }
    
    static public ArrayList<String> readLines(String fn){
        try {
            return Files.lines(Paths.get(fn)).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
        catch (Exception e){
            return new ArrayList<String>();
        }
    }

    static public String readLinesToStr(String fn){
        StringBuilder ret = new StringBuilder();
        for (String s:readLines(fn))
            ret.append(ret.length()==0?"":"\n").append(s);
        return ret.toString();
    }
}
    
// --------------------------------------------------------------------------------------------------------------

class CodeAnalyser {
    public static Pattern methDataPat = Pattern.compile("  (.+?);\n    descriptor:(.+?)\n    flags:(.*?)\n" // flags: (.+?)  -> flags: (.*?) because of older javap on CO-server
                                                       +"    Code:\n      stack=([0-9]+), locals=([0-9]+), args_size=([0-9]+)\n"
                                                       +"((?:\\s*[0-9]+: .*?\n)*?)      LineNumberTable:\n((?:\\s*line [0-9]+: [0-9]+\n)*)");

    public static Pattern lineNumbPat = Pattern.compile("^\\s*line ([0-9]+): ([0-9]+)\\s*$");
    public static Pattern codeLinePat = Pattern.compile("^\\s*([0-9]+): ([^\\s]+?)(?:|\\s+(.*?)(|//(.*?)))$");

    public static String[] operationsWithFloats = {"dmul", "dadd", "ddiv", "dsub", "i2d", "d2i", "dload"
                                                  ,"fmul", "fadd", "fdiv", "fsub", "i2f", "f2i", "fload"};
    
    // for details about Java bytecode see e.g.: https://en.wikipedia.org/w/index.php?title=Java_bytecode&oldid=1144728510
    class ByteCodeEntry {
        int bcIndex;
        int javaLineNo;
        String op;
        String args;
        String comment;
        
        Integer arg1Val;
        ByteCodeEntry(int i, String o, String a, String c){
            bcIndex=i; op=o; args=a; comment=c; 
        }
        Integer loopsTo(){ // for & while use "goto"; a doWhile has a conditional jump at the end (if_***, e.g. if_icmple)
            return arg1Val!=null && arg1Val<bcIndex && (op.equals("goto") || op.startsWith("if_")) ? arg1Val : null;
        }
    }
    
    class MethDat {
        Integer lineNoBeg;
        Integer lineNoEnd;
        
        String methByteCodeDataStr;
        String sourceCodeStr;
        
        String signatureStr;
        String signatureMin;
        String descriptor;
        String flags;
        String stack;
        String locals;
        String args_size;
        String byteCodeStr;
        String lineNoTable;
        
        ArrayList<ByteCodeEntry> bcList = new ArrayList<ByteCodeEntry>();
        TreeMap<Integer, ByteCodeEntry> bcMap_idx2bce = new TreeMap<Integer, ByteCodeEntry>();
        TreeMap<Integer, Integer> javaLn2bcIdx = new TreeMap<Integer, Integer>();
        TreeMap<Integer, Integer> bcIdx2javaLn = new TreeMap<Integer, Integer>();
    }
    
    TreeMap<String, MethDat> dat = new TreeMap<String, MethDat>();
    String clsNam;
    String classByteCodeDataStr;
    ArrayList<String> javaCodeLines;
    String[] javaCodeLinesWithoutComments;
    static boolean dbg = false;
    
    CodeAnalyser(String className){
        clsNam = className;
    }
    //                                                           1=optMod        2=retType    3=name     4=params    5=rest 
    static public Pattern pat_javapSignature = Pattern.compile("^(?:(.*?)\\s|\\s*)([^\\s]+)\\s+([^\\s]+)\\(([^)]*)\\)(.*)$");
    static public String javapSignatureToMinSignature(String s){
        if (dbg)
            Check.sopl("javapSignatureToMinSignature("+s+")");
        
        boolean tmpDbg = s.indexOf("xxxxxxxxxxxxxxsum_rec")>=0;
        if (tmpDbg)
            Check.sopl("\n-----------------------------\nstarted javapSignatureToMinSignature("+s+")");
            
        Matcher m = pat_javapSignature.matcher(s);
        if (!m.matches())
            return s;
        
        if (dbg||tmpDbg)
            Check.sopl("javapSignatureToMinSignature("+s+"):\n    1:"+m.group(1)+"\n    2:"+m.group(2)+"\n    3:"+m.group(3)+"\n    4:"+m.group(4)+"\n    5:"+m.group(5));
        
        String[] paStrs = m.group(4).split(","); // when errors occure we could also use Check.splitValueList
        String ret="";
        for (String paStr:paStrs)
            ret+=(ret.length()==0?"":",")+Check.signatureParamTypeTrafo_java2human(paStr).trim();
        return m.group(3)+"("+ret+")";
    }
    
//     static public String javapSignatureToHumanMin_withModifiersAndRettype(String s){
//         if (dbg)
//             Check.sopl("javapSignatureToHumanMin("+s+")");
//         Matcher m = pat_javapSignature.matcher(s);
//         if (!m.matches())
//             return s;
//         
//         if (dbg)
//             Check.sopl("javapSignatureToHumanMin("+s+"):\n    "+m.group(1)+"\n    "+m.group(2)+"\n    "+m.group(3)+"\n    "+m.group(4)+"\n    "+m.group(5));
//         
//         String[] paStrs = m.group(4).split(","); // when errors occure we could also use Check.splitValueList
//         String ret="";
//         for (String paStr:paStrs)
//             ret+=(ret.length()==0?"":",")+Check.signatureParamTypeTrafo_java2human(paStr).trim();
//         return m.group(3)+"("+ret+")";
//     }
    
    MethDat getDatBySignature(String s){
        if (dat.size()==0){ // lazy initialization (only started if it is realy needed;-)
            // 1. get the output of javap for the class
            RTFunc.exec("javap -v -c -p -l "+clsNam+".class >bc.txt", true, false);
            classByteCodeDataStr = RTFunc.readLinesToStr("bc.txt");
            javaCodeLines = RTFunc.readLines(clsNam+".java");
            javaCodeLinesWithoutComments = Check.stripJavaComments(String.join("\n", javaCodeLines), true).split("\n");
            if (false){
                for (int i=0; i<javaCodeLinesWithoutComments.length; i++){
                    String ln=i+1+""; while (ln.length()<3) ln=" "+ln; ln+=" : ";
                    Check.sopl(ln+javaCodeLinesWithoutComments[i]);
                }
            }                          
                                                
            // 2. separate info for the single methods 
            Matcher m=methDataPat.matcher(classByteCodeDataStr);
            while (m.find()){
                if (dbg)
                    Check.sopl("fnd "
                         //   +"\n---------\n"+m.group(0)+"\n------------\n"
                            +"\n    si:"+m.group(1)
                            +"\n    de:"+m.group(2)
                            +"\n    fl:"+m.group(3)
                            +"\n    st:"+m.group(4)
                            +"\n    lo:"+m.group(5)
                            +"\n    as:"+m.group(6)
                        );
                
                // 1. parse the basic method data parts
                MethDat md = new MethDat();
                        md.methByteCodeDataStr = m.group(0);
                        md.signatureStr = m.group(1).trim();
                        md.descriptor   = m.group(2).trim();
                        md.flags        = m.group(3).trim();
                        md.stack        = m.group(4).trim();
                        md.locals       = m.group(5).trim();
                        md.args_size    = m.group(6).trim();
                        md.byteCodeStr  = m.group(7).trim();
                        md.lineNoTable  = m.group(8).trim();
                
                md.signatureMin = javapSignatureToMinSignature(md.signatureStr);
                dat.put(md.signatureMin, md);

                // 2. extract begin and end line numbers, fill mappings for both directions (javaLn2bcIdx and bcIdx2javaLn)
                Integer min=null, max=null;
                for (String tabLine:md.lineNoTable.split("\n")){ // e.g. >>>        line 34: 30<<<
                    Matcher mLn = lineNumbPat.matcher(tabLine);
                    if (!mLn.matches()){
                        if (dbg)
                            Check.sopl("CodeAnalyser has LineNumberTable parsing problem for tabLine >>>"+tabLine+"<<<");
                        continue;
                    }
                    Integer i1 = Integer.parseInt(mLn.group(1));  // the line number in the java source code
                    Integer i2 = Integer.parseInt(mLn.group(2));  // byte code index (starts at 0)
                    min = min==null ? i1 : (i1==null ? min : (min<i1 ? min : i1));
                    max = max==null ? i1 : (i1==null ? max : (max>i1 ? max : i1));
                
                    md.javaLn2bcIdx.put(i1, i2);
                    md.bcIdx2javaLn.put(i2, i1);
                }
                md.lineNoBeg = min;
                md.lineNoEnd = max;
                if (dbg)
                    Check.sopl("  --> @ "+md.lineNoBeg+" ... "+md.lineNoEnd);
                md.sourceCodeStr = null; // will be filled if needed in sourceCodeForSignature(String s)
                    
                // 3. extract byte code entries and attach line numbers
                for (String codeLine:md.byteCodeStr.split("\n")){ // e.g. >>>        43: goto          25<<<
                    Matcher mLn = codeLinePat.matcher(codeLine);
                    if (!mLn.matches()){
                        if (dbg)
                            Check.sopl("CodeAnalyser has byteCodeStr parsing problem for codeLine >>>"+codeLine+"<<<");
                        continue;
                    }
                    
                    ByteCodeEntry bce = new ByteCodeEntry
                            ( Integer.parseInt(mLn.group(1))  // the byte code index (starts at 0)
                            ,                  mLn.group(2)   // the operator
                            ,                  mLn.group(3)   // optional arguments
                            ,                  mLn.group(4)   // optional comment
                            );
                    bce.arg1Val    = null; try { bce.arg1Val = Integer.parseInt(bce.args.split(",")[0].trim()); } catch(Exception e){} // keep null on error
                    bce.javaLineNo = md.bcIdx2javaLn.get(md.bcIdx2javaLn.floorKey(bce.bcIndex)); // get javaLineNo for same or smaller bc (only stored for fst bc per line) 
                    md.bcList.add(bce);
                    md.bcMap_idx2bce.put(bce.bcIndex, bce);
                }
                
                // further debugging tests
                if (dbg){
                    Check.sopl(md.signatureMin+" uses loops  : "+usesSuchLoop                  (md.signatureMin, null));
                    Check.sopl(md.signatureMin+" uses floats : "+usesFloats                    (md.signatureMin      ));
                    Check.sopl(md.signatureMin+" starts at   : "+getFirstCodeLineNoForSignature(md.signatureMin      ));
                }
            }
        }
        return dat.get(s);
    }

    // -------- some useful helpers ----------------

    String sourceCodeForSignature(String s){    // to give a hint at which line number the method (inner code lines) can be found
        MethDat md = getDatBySignature(s);
        if (md==null)
            return null;
        if (md.sourceCodeStr==null){
            StringBuilder r = new StringBuilder();
            for (int jLn=md.lineNoBeg; jLn<=md.lineNoEnd; jLn++)
                r.append(jLn==md.lineNoBeg?"":"\n").append(javaCodeLinesWithoutComments[jLn-1]);  // -1 is pos -> idx
            md.sourceCodeStr = r.toString();
        }
        return md.sourceCodeStr;
    }

    boolean containsAtLeastOneOperation(String s, String[] opArr){ // todo: should use bcList and compare op of each entry
        for (String op : opArr)
            if (s.indexOf(": "+op)>=0)
                return true;
        return false;
    }

    Integer getFirstCodeLineNoForSignature(String s){    // to give a hint at which line number the method (inner code lines) can be found
        MethDat md = getDatBySignature(s);
        return md==null ? null : md.lineNoBeg;
    }

    Boolean usesFloats(String s){               // may be good when use of floats is forbidden
        MethDat md = getDatBySignature(s);
        if (md==null || md.byteCodeStr==null)
            return null;
        return containsAtLeastOneOperation(md.byteCodeStr, operationsWithFloats); // todo -> use bcList!!!
    }
    
    Pattern pat_loopBeg = Pattern.compile("\\b(for|do|while)\\b"); // We use word boundries \\b to avoid detection of do as loop type in: for /* ccc*/(int i_do=1; i_do<=x; i_do++)
    Boolean usesSuchLoop(String s, String[] loopKeywords){ // keys may be "for", "do" or "while"; no or empty array -> any loopBack returns true
        // Check.sopl("SrcCode of "+s+":\n"+sourceCodeForSignature(s));
        
        MethDat md = getDatBySignature(s);
        if (md==null)
            return null;
        for (ByteCodeEntry bce:md.bcList){
            Integer loopBegIdx = bce.loopsTo();
            if (loopBegIdx==null)
                continue;   // no loop found at this entry
                
            if (loopKeywords==null || loopKeywords.length==0) // found some loop, type is not important -> ready 
                 return true;
            
            // we found a byte code position that leads to the begin of a loop -> check the source code for keywords there
            Integer javaLnNo = md.bcMap_idx2bce.get(loopBegIdx).javaLineNo; 
            // note: the line we get now may have crazy formating (all in one line), comments, var names including keywords...
            //   -> we have to normalize (remove comments - but keep line numbers!) or finally demand better formating
            //        e.g. do { a++; } while (a<10);    will find while keyword too!!!

            // NOTE: the following tests may still be wrong in (very?) special cases, e.g. bad formating... (TODO????) -> only a "good?" guess!!! 
            for (int jLn=javaLnNo-1; jLn>=0; jLn--){    // -1 is pos -> idx
                String pure = javaCodeLinesWithoutComments[jLn];  
                // Check.sopl("jLn:"+jLn+"  stripped:"+pure);
                
                Matcher mLb = pat_loopBeg.matcher(pure);
                String lstLoopType = "";  // may be there are other loops before (in the same line) -> use the last one (bad for do while as oneliner???)
                while (mLb.find())
                    lstLoopType=mLb.group(1);
                Check.sopl("found loop type is: "+lstLoopType);
                if (!"".equals(lstLoopType)){
                    for (String kw:loopKeywords)
                        if (kw.equals(lstLoopType))
                            return true;    // used one of the given types
                    return false; // used another
                }
            }
        }
        return false; 
    }

    Boolean usesKey(String s, String key){ // keys may be "for", "do" or "while"; no or empty array -> any loopBack returns true
        String src = sourceCodeForSignature(s);
        if (src==null)
            return null;
        String tagsforSrc = Check.tagSrc(src); 
        String patchedSrc = "";
        for (int o=0; o<src.length(); o++)
            patchedSrc+=tagsforSrc.charAt(o)!=' ' ? ' ' : src.charAt(o);
        // Check.sopl("SrcCode of "+s+":\n"+src);
        // Check.sopl("patched SrcCode of "+s+":\n"+src);
        return Pattern.compile("\\b("+key+")\\b").matcher(patchedSrc).find();
    }
}

// --------------------------------------------------------------------------------------------------------------

public class Check {

    CodeAnalyser codeAnalyserStu;   // students code 
    CodeAnalyser codeAnalyserRef;   // code in reference solution

    public class Result {
        boolean isOk;
        String  info;
        String  thisClsNam;      // must be stored here because we may check multiple classes at once
        Integer lineNo;
        ContextInfo ctx;    // here we have (if available) the type of test, the signature  ...
        
        Result(boolean ok, ContextInfo ctx, String txt){
            isOk = ok;
            info = txt;
            this.ctx = ctx;
            thisClsNam = clsNam;  // getEnclosingClass
            if (ctx!=null)
                lineNo = codeAnalyserStu.getFirstCodeLineNoForSignature(ctx.signature);
            if (ctx==null || ctx.config==null || !ctx.config.noResultAutoAdd)
                addToResultList(this);
        }
        
        boolean ok(){
            return isOk;
        }
        boolean hasError(){
            return !isOk;
        }
        String getInfo(){
            return info;
        }
        String getPosition(){
            String fn = thisClsNam+".java";
            return lineNo==null ? ("in "+fn) : ("in "+fn+":"+lineNo);
        }
        String getTestName(){
            return ctx==null ? "" : ctx.getTestName();
        }
    }
    
    public class ContextInfo { // stores hints where we found a problem
        String signature;
        String typeOfTest;
        String lastOutput;
        ConfigData config;
        
        ContextInfo(String s, String t, ConfigData c){
            signature  = signatureNormalization(s); // store a human readable normalized version -> e.g. important to find line numbers in the CodeAnalyzer  etc
            typeOfTest = t;
            config     = c;
        }
        
        ContextInfo(String s, String t){
            signature  = signatureNormalization(s); // store a human readable normalized version -> e.g. important to find line numbers in the CodeAnalyzer  etc
            typeOfTest = t;
        }
        
        String getContext(){
            boolean sOk =  signature !=null && !signature .trim().equals("");
            boolean tOk =  typeOfTest!=null && !typeOfTest.trim().equals("");
            
            return sOk && tOk ? (typeOfTest + " von " + signature)
                              : (sOk ? signature : (tOk ? typeOfTest : ""));
        }
        
        String getTestName(){
            return getContext();
        }
        
        String getStr_the_kindOfMember(){
            if (refChkF.get(signature)!=null) return "Die Variable";
            // String clsNam = stuSol.getName();  take clsNam from Check directly is save but asking stuSol IS NOT (may be it did not compile!!!!); 
            return signature.indexOf(clsNam)==0 && signature.charAt(clsNam.length())=='(' ? "Der Konstuktor" : "Die Methode";
        }
    }
    
    public class ConfigData { // may be used for Check-class (todo) and to parametrize certain tests (e.g. in a call of checkReturnValue)
        String[] scopes = {};                    // a list of scopes. Note: the values in the array should in general not be changed (only be used for transfer)

        // All these values are null because every new config shall have no defaults. This makes combining config of Check and config of a certain test easier to understand.
        Double  floatEps                                = null;
        Boolean quoteCharInErr                          = null;
        Boolean quoteStringInErr                        = null;
        Boolean noResultAutoAdd                         = null;
        Integer dbgLevel                                = null;
        Integer timeOutInMs                             = null;
        Boolean char2dimAsciiArt                        = null;       // show char[][] and Character[][] as ASCII-art (no ' or , and \n before first and after each line)
        Function_specialCompareOfResult specCompareFunc = null;

        String  nullStrInAsciiArt = "\u20E0";   // lets try if students understand this: Unicode Character 'COMBINING ENCLOSING CIRCLE BACKSLASH' (U+20E0)
        
        public String toString(){
            return "ConfigData(epsF:"  +floatEps
                           +", chrQ:"  +quoteCharInErr
                           +", strQ:"  +quoteStringInErr
                           +", nAdd:"  +noResultAutoAdd
                           +", dbgL:"  +dbgLevel
                           +", toMs:"  +timeOutInMs
                           +", asci:"  +char2dimAsciiArt
                           +", sCmp:"  +specCompareFunc
                           +", scopes:"+a2s(scopes)+")";
        }
        
        ConfigData(){   // no params here -> use setters where needed
        }

        // setter with nice long names
        ConfigData set_floatEps        (Double                          x) { floatEps            = x ; return this; }   
        ConfigData set_quoteCharInErr  (Boolean                         x) { quoteCharInErr      = x ; return this; }   
        ConfigData set_quoteStringInErr(Boolean                         x) { quoteStringInErr    = x ; return this; }   
        ConfigData set_noResultAutoAdd (Boolean                         x) { noResultAutoAdd     = x ; return this; }   
        ConfigData set_dbgLevel        (Integer                         x) { dbgLevel            = x ; return this; }   
        ConfigData set_timeOutInMs     (Integer                         x) { timeOutInMs         = x ; return this; }   
        ConfigData set_char2dimAsciiArt(Boolean                         x) { char2dimAsciiArt    = x ; return this; }   
        ConfigData set_specCompareFunc (Function_specialCompareOfResult x) { specCompareFunc     = x ; return this; }
        ConfigData set_scope           (String                          x) { scopes = new String[]{x}; return this; }

        // short cuts of the above setters for more compact code
        ConfigData epsF    (Double                          x) { return set_floatEps        (x); }
        ConfigData chrQ    (Boolean                         x) { return set_quoteCharInErr  (x); }
        ConfigData strQ    (Boolean                         x) { return set_quoteStringInErr(x); }
        ConfigData nAdd    (Boolean                         x) { return set_noResultAutoAdd (x); }
        ConfigData dbgL    (Integer                         x) { return set_dbgLevel        (x); }
        ConfigData toMs    (Integer                         x) { return set_timeOutInMs     (x); }
        ConfigData asciiArt(Boolean                         x) { return set_char2dimAsciiArt(x); }
        ConfigData spCmpFnc(Function_specialCompareOfResult x) { return set_specCompareFunc (x); }
        ConfigData scope   (String                          x) { return set_scope           (x); }
        
        boolean dbg(int lev){
            return lev<=dbgLevel;
        }
        
        boolean hasScope(){
            return scopes!=null && scopes.length>0;
        }
        
        ConfigData mergeWithDefaults(ConfigData def){
            ConfigData ret = new ConfigData();
            
            // 1. handle single config parameters
            if      (this.floatEps        !=null) ret.floatEps         = this.floatEps        ;
            else if ( def.floatEps        !=null) ret.floatEps         =  def.floatEps        ; // else we keep the general default

            if      (this.quoteCharInErr  !=null) ret.quoteCharInErr   = this.quoteCharInErr  ;
            else if ( def.quoteCharInErr  !=null) ret.quoteCharInErr   =  def.quoteCharInErr  ; // else we keep the general default

            if      (this.quoteStringInErr!=null) ret.quoteStringInErr = this.quoteStringInErr;
            else if ( def.quoteStringInErr!=null) ret.quoteStringInErr =  def.quoteStringInErr; // else we keep the general default

            if      (this.noResultAutoAdd !=null) ret.noResultAutoAdd  = this.noResultAutoAdd ;
            else if ( def.noResultAutoAdd !=null) ret.noResultAutoAdd  =  def.noResultAutoAdd ; // else we keep the general default

            if      (this.dbgLevel        !=null) ret.dbgLevel         = this.dbgLevel        ;
            else if ( def.dbgLevel        !=null) ret.dbgLevel         =  def.dbgLevel        ; // else we keep the general default

            if      (this.timeOutInMs     !=null) ret.timeOutInMs      = this.timeOutInMs     ;
            else if ( def.timeOutInMs     !=null) ret.timeOutInMs      =  def.timeOutInMs     ; // else we keep the general default

            if      (this.char2dimAsciiArt!=null) ret.char2dimAsciiArt = this.char2dimAsciiArt;
            else if ( def.char2dimAsciiArt!=null) ret.char2dimAsciiArt =  def.char2dimAsciiArt; // else we keep the general default
        
            if      (this.specCompareFunc !=null) ret.specCompareFunc  = this.specCompareFunc ;
            else if ( def.specCompareFunc !=null) ret.specCompareFunc  =  def.specCompareFunc ; // else we keep the general default
        
            // 2. handle scope Ids -> merge both; NOTE: usually we merge with Check.defaultConfigData, which has no special scopes.
            if (def.scopes==null || def.scopes.length==0)       // nothing to merge, because no scopes in def 
                ret.scopes = this.scopes;                       //  -> use this.scopes
            else {
                if (this.scopes==null || this.scopes.length==0) // nothing to merge, because no scopes in this 
                    ret.scopes = def.scopes;                    //  -> use def.scopes
                else {                                          // we have scopes in both -> merge
                    ret.scopes = new String[this.scopes.length + def.scopes.length];
                    int i=0;
                    for (String s:this.scopes)
                        ret.scopes[i++]=s;
                    for (String s:def.scopes)
                        ret.scopes[i++]=s;
                }
            }
            return ret;
        }
    }
    
    // these methods in Check may be used to change the values of the default config data in THIS Check 
    Check epsF    (Double                          x) { if (x==null) x=1e-7; defaultConfigData.set_floatEps        (x); return this; }
    Check chrQ    (Boolean                         x) { if (x==null) x=true; defaultConfigData.set_quoteCharInErr  (x); return this; }
    Check strQ    (Boolean                         x) { if (x==null) x=true; defaultConfigData.set_quoteStringInErr(x); return this; }
    Check nAdd    (Boolean                         x) { if (x==null) x=true; defaultConfigData.set_noResultAutoAdd (x); return this; }
    Check dbgL    (Integer                         x) { if (x==null) x=0   ; defaultConfigData.set_dbgLevel        (x); return this; }
    Check toMs    (Integer                         x) { if (x==null) x=100 ; defaultConfigData.set_timeOutInMs     (x); return this; }  // todo: check on CodeOcean server if this is usually ok for e.g. larger array alogos too...
    Check asciiArt(Boolean                         x) { if (x==null) x=true; defaultConfigData.set_char2dimAsciiArt(x); return this; }
    Check spCmpFnc(Function_specialCompareOfResult x) {                      defaultConfigData.set_specCompareFunc (x); return this; }  // each value is accepted
    Check scope   (String                          x) {                      defaultConfigData.set_scope           (x); return this; }
    
    public ConfigData config(){
        return new ConfigData();
    }
    
    public ConfigData merge(ConfigData cd){
        return cd==null ? defaultConfigData : cd.mergeWithDefaults(defaultConfigData);
    }
    
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //   
    //  Some enums which are used to describe the results of basic checks concerning the class and the methods 
    // 

    public enum classState {
        unchecked,
        noFile,         // implies: checked (and seen that there is no file with Java code)
        notCompiled,    // implies: file exists
        compiled        // implies: file exists
    }
    
    public enum elemsState {
        unchecked,
        loaded
    }

    
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //   
    //  Member variables for the check of a certain student class
    // 

    boolean gaveMsg_notCompiled = false,    // to avoid that such a message is given for each single test again
            gaveMsg_noFile      = false;
            
    String    clsNam;   // the class name (the name of the class we have to check)
    Class<?>  refSol;   // the reference solution class
    Class<?>  stuSol;   // the class with the student solution; NOTE: use classOk() to fill this variable
    classState clsIs;   // this state decribes the result of loading stuSol (the students solution class) 
    elemsState elmIs;   // this state decribes the result of loading methods from stuSol into elmIs

    ConfigData defaultConfigData        // the default values for this Check, e.g. for floatEps etc; may be changed temporarilly by certain method calls
                = config().dbgL(0)      // we do not want debug output by default
                          .toMs(100)    // this might be a good value for most cases (else it must be increased)
                          .chrQ(true)   // this makes in general sense, compare e.g. (...it should be a and not b...) vs. (...it should be 'a' and not 'b'...)
                          .strQ(true)   // this makes in general sense, compare e.g. (...it should be abc and not def...) vs. (...it should be "abc" and not "def"...)
                          .nAdd(false)  // auto add of results is currently the normal behaviour -> important for transfer to CodeOcean; switch off add to use Results without telling CodeOcean..
                          .epsF(1e-7)   // usually a good value; overwrite it in the junit file for the whole Check or certain tests if needed.
                          ;
                     
    TreeMap<String, Executable> stuAllE = new TreeMap<String, Executable>(); // all executables (methods and constructors) found in the students class, may be used to find similar names
    TreeMap<String, Executable> refChkE = new TreeMap<String, Executable>(); // all executables (methods and constructors) from refSol which are marked as to be checked   
    TreeMap<String, Field     > stuAllF = new TreeMap<String, Field     >(); // all fields found in the students class, may be used to find similar names
    TreeMap<String, Field     > refChkF = new TreeMap<String, Field     >(); // all fields from refSol which are marked as to be checked   
    
    long timeMs_start = 0;    
    long timeMs_end   = 0;    
    ArrayList<Result> resultList = new ArrayList<Result>();


    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //   
    //  General methods for checking the class, loading methods etc
    // 

    public Check(String clsNam, Class refSol){
        changeClasses(clsNam, refSol, true);
    }
    
    public Check changeClasses(String clsNam, Class refSol, boolean resetTime){
        stuAllE = new TreeMap<String, Executable>();
        refChkE = new TreeMap<String, Executable>();
        stuAllF = new TreeMap<String, Field     >();
        refChkF = new TreeMap<String, Field     >();
        clsIs = null;
        elmIs = null;

        this.refSol = refSol;
        this.clsNam = clsNam;

        // 2. fill refChkE and refChkF with those in refSol which are marked as to be checked
        Method[] rma = refSol.getDeclaredMethods();
        for (Method m:rma)
            if (hasAnnotation(m, CheckIt.class))
                refChkE.put(calcSignature(m), m);   // false: not for humans 
        Constructor[] rca = refSol.getDeclaredConstructors();
        for (Constructor c:rca)
            if (hasAnnotation(c, CheckIt.class))
                refChkE.put(calcSignature(c), c);   // false: not for humans 

        Class tmpCls = refSol;
        do {
            Field[] rfa = tmpCls.getDeclaredFields();
            for (Field f:rfa)
                if (hasAnnotation(f, CheckIt.class))
                    refChkF.put(f.getName(), f);        
            tmpCls = tmpCls.getSuperclass();
        } while(!tmpCls.equals(Object.class));

        // 3. check all annotations if they can be parsed and used correctly
        codeAnalyserRef = new CodeAnalyser(refSol.getName());
        checkRefSol_stopOnError(true); // -> true means: yes, stop if there is an error 
        
        // 4. initialize the source code analyser, note: analysis is started later on demand
        codeAnalyserStu = new CodeAnalyser(clsNam);
        
        // 5. set the start time for the statistics
        if (resetTime)
            timeMs_start = System.currentTimeMillis();
    
        return this;
    }
    
    public TestParamSet[] getTestParamSetArrFor(Executable x){
        TestParamSets tpss = x.getAnnotation(TestParamSets.class);
        if (tpss!=null) 
            return tpss.value();
        TestParamSet tps = x.getAnnotation(TestParamSet.class);
        return tps==null ? new TestParamSet[0] : new TestParamSet[]{tps};
    }
    
    public int getTimeout(Executable x, Integer defVal){
        if (defVal==null)
            defVal=400;
        TimeOutMs toMs = x.getAnnotation(TimeOutMs.class);
        return toMs==null ? defVal : (toMs.value()<=0 ? defVal : toMs.value());
    }
            
    public String lnhAnnot(Integer lineNoHint){
        return lineNoHint==null ? "" : (" (see annotations before line "+lineNoHint+" in "+refSol.getName()+".java)");
    }
    
    public String checkRefSol_stopOnError(boolean stopOnError){ // perform consistency checks

        // ----------------------------------------------------------------------------------------
        // TODO: ensure that each test in *_check.java should has all needed data in the refSol (in case we use junit)
        // ----------------------------------------------------------------------------------------
    
        // iterate over all methodes which have the CheckIt annotation -> those are stored in refChkE
        // General constraints:
        //   - each method with CheckIt needs a TestParamSet unless it has no parameters
        //     TODO: or it uses object variables
        //   - ...
        
        // new Exception("here checkRefSol_stopOnError was called ...").printStackTrace();
        
        boolean locDbg = false; // todo: make controlable from outside -> problematic when called in constructor!!! -> any idea???
        
        StringBuilder ret = new StringBuilder();
        ret.append("Checking consistency of class ").append(refSol.getName()).append(" which is used to evaluate ").append(clsNam).append("\n");
        int err = 0;
        int chk = 0;
        
        for (Executable x : refChkE.values()){ // for m we have to perform certain consistency checks
            String signat = calcSignature(x, true); // true: for humans
            Class[] paTys = x.getParameterTypes();  // must fit the annotations for values and ranges 
            Integer lineNoHint = codeAnalyserRef.getFirstCodeLineNoForSignature(signat);
            
            // 1. TestParamSet
            chk++;
            if (locDbg) sopl("checkRefSol_stopOnError() exec="+signat+" checks if it has at least one TestParamSet");
            TestParamSet[] tePaSetArr = getTestParamSetArrFor(x);
            if (tePaSetArr.length==0){ // has @CheckIt-annotation but no TestParamSet -> must have at least one, even for an empty parameter list!
                if (false){ // todo make configurable
                    String Q="ERROR: has @CheckIt-annotation but no TestParamSet found"+lnhAnnot(lineNoHint)+"\n  --> you must have at least one, even if it is only an empty parameter list!"; ret.append(Q+"\n"); sopl(Q);
                    err++;
                }
            }
            else { // iterate over the single sets
                for (TestParamSet tps : tePaSetArr){
                //  1a. scopes-annotations do not need a check, because if the parameter set has no scope, then it can/will be used overall
                    String scopes = scopeArrayToList(tps.scopes(), "", ", ", "");  // for debugging
                           scopes = (scopes==null) ? "" : (", scope=["+scopes+"]");
                    
                //  1b. values-annotations must be parseble according to the method parameter types
                    String[] valuesStrArr = tps.values();
                    for (String s: valuesStrArr){
                        chk++;
                        if (locDbg) sopl("checkRefSol_stopOnError() meth="+signat+scopes+" checks TestParamSet.values :"+s);
                        try {
                            /* Object[] paArr = */ objArrFromParamStr(paTys, s);
                            // was obviously ok ;-)
                        }
                        catch (Exception e){
                            String Q="ERROR: Found bad value definition for "+signat+" in: "+s+lnhAnnot(lineNoHint)+"\n"+e; ret.append(Q+"\n"); sopl(Q);
                            err++;
                        }
                    }

                //  1c. all parameters in the ranges annotations must be parseble according to the method parameter types
                    String[] rangesStrArr = tps.ranges();
                    for (String s: rangesStrArr){
                        chk++;
                        if (locDbg) sopl("checkRefSol_stopOnError() meth="+signat+scopes+" checks TestParamSet.ranges :"+s);
                        Range[] ranges = Range.rangeArrFromStr(paTys, s); // try to parse a set of range data for the parameters
                        try {
                            Range.checkRangeArray(paTys, ranges);
                        }
                        catch (Exception e){
                            String Q="ERROR: Found bad range definition in: "+s+lnhAnnot(lineNoHint)+"\n"+e; ret.append(Q+"\n"); sopl(Q);
                            err++;
                        }
                    }
                    
                //  1cd all parameters in the fields annotations must be parseble according to fields and the method parameter types
                    String[] fieldsStrArr = tps.fields();
                    if (fieldsStrArr.length>0){    // note: with no fields we will get array length 0
                        if (fieldsStrArr.length==1){ 
                            String Q="ERROR: Found bad fields definition for "+signat+" (must be at least \"field name list\", \"field value list\") in: "
                                    +lnhAnnot(lineNoHint); ret.append(Q+"\n"); sopl(Q);
                            err++;
                        }
                        else {
                            String[] fieldNames = fieldsStrArr[0].split(",");
                            if (fieldNames.length<1){ 
                                String Q="ERROR: Found bad fields definition for "+signat+" (must be at least 1 field name in first string) in: "
                                        +lnhAnnot(lineNoHint); ret.append(Q+"\n"); sopl(Q);
                                err++;
                            }
                            else {
                                // check if the field names are ok and get the type list for them 
                                Class[] fiTys = new Class[fieldNames.length];
                                for (int i=0; i<fieldNames.length; i++){
                                    String nam = fieldNames[i].trim();
                                    Field f = refChkF.get(nam);
                                    if (f==null){
                                        String Q="ERROR: Found bad fields definition for "+signat+" (unknown field name \""+nam+"\" in first string) in: "
                                                +lnhAnnot(lineNoHint); ret.append(Q+"\n"); sopl(Q);
                                        err++;
                                        fiTys=null; // skip further tests
                                        break;
                                    }
                                    fiTys[i] = f.getType(); 
                                }
                                if (fiTys!=null){ // check the parameter ists now
                                    Class[] fiPaTys = concat(fiTys, paTys);
                                    for (int i=1; i<fieldsStrArr.length; i++){
                                        String s = fieldsStrArr[i];
                                        chk++;
                                        if (locDbg) sopl("checkRefSol_stopOnError() meth="+signat+scopes+" checks TestParamSet.fields :"+s);
                                        try {
                                            /* Object[] paArr = */ objArrFromParamStr(fiPaTys, s);
                                            // was obviously ok ;-)
                                        }
                                        catch (Exception e){
                                            String Q="ERROR: Found bad fields definition for "+signat+" in: "+s+lnhAnnot(lineNoHint)+"\n"+e; ret.append(Q+"\n"); sopl(Q);
                                            err++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    
                    
                                        
                // 1e. there must be at least one ranges or one values annotation    
                    chk++;
                    if (locDbg) sopl("checkRefSol_stopOnError() meth="+signat+scopes+" checks if we have found at least one ranges or one values annotation");
                    if (valuesStrArr.length + rangesStrArr.length  + fieldsStrArr.length == 0){
                        String Q = "ERROR: Found a TestParamSet with no range/value/fields definition"+lnhAnnot(lineNoHint); ret.append(Q+"\n"); sopl(Q);
                        err++;
                    }
                }
                
                if (true){
                    // test with new style... (test the test: may be the old above will be removed later... ;-) 
                    ExecFiPaDat[] datV = getExecFiPaDatFor(x, null, lineNoHint, 'v'); // checks values()
                    for (ExecFiPaDat dat : datV)
                        for (String e : dat.errs){
                            sopl(e);
                            // err++;
                        }

                    ExecFiPaDat[] datF = getExecFiPaDatFor(x, null, lineNoHint, 'f'); // checks fields()
                    for (ExecFiPaDat dat : datF)
                        for (String e : dat.errs){
                            sopl(e);
                            // err++;
                        }
                }
            }
            
            // 2. Variants  todo!!!!!!!!!!!!!!!!
            //  2a. otherTypes
            //  2b. modifierChecks  
        }
        
        if (locDbg)
            sopl("checkRefSol_stopOnError() performed "+chk+" checks and found "+err+" error(s) -> "+(err==0?"OK":"!!!!!!!!!!!!!!!!!!!!!!!!"));
        
        String resultStr = ret.append(err==0?"OK":(err+" errors found!!!")).toString();
        if (stopOnError && err>0){
            sopl(resultStr+"\n Stopping now!!!!!!!!!!!!!!!!!!\n   -> you have to fix the problems in "+refSol.getName()+".java first!");
            System.exit(0);
        }

        return resultStr;
    }

    public static <T> T[] concat(T[] a, T[] b){
        T[] r = Arrays.copyOf(a, a.length+b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    public static Object deepCopy(Object a){ // lets clone it (deeply for arrays) -> TODO: implement deep copy also for other reference variables -> all the memebers!!!
        if (a==null)
            return a;
        if (a.getClass().isArray()){
        	int n = Array.getLength(a);
        	// sopl("xxxxxx "+a.getClass()+" cty:"+a.getClass().getComponentType()+" len:"+n);
            Object r = Array.newInstance(a.getClass().getComponentType(), n); //  new Object[Array.getLength(a)];
            for (int i=0; i<n; i++)
            	Array.set(r, i, deepCopy(Array.get(a, i)));
            return r;
        }            
        
        // Here we have todo a lot!
        // Currently this works only for primitives correctly.
        // For reference variables we have to make a deep copy of all members too!!!!!!!!!!!!!!!!!!!!
        return a;       

    }
    
    public String toStr(String[] a, int f, int l, String sep){
        StringBuilder r=new StringBuilder();
        for (int i=f; i<l; i++)
            r.append(r.length()==0?"":sep).append(a[i]);
        return r.toString();
    }
    
    public class ExecFiPaDat {  // fiStr.len==paStr.len==fpStr.len
        String[] fiNamArr = {};    // array of used field names; empty when filled by values() annotation
        Class [] fiTypArr = {};    // array of field types     (one entry per name)
        Class [] paTypArr = {};    // array of parameter types (one entry per executable parameter)
        Class [] fpTypArr = {};    // array of field+parameter types (may be used e.g. to check the according fpStrArr)
       
        String[] fiStrArr = {};    // array of field value lists           (to init fields which are used in the executable; comma separated)
        String[] paStrArr = {};    // array of parameter value lists       (to init the parameters of the executable; comma separated)
        String[] fpStrArr = {};    // array of field+parameter value lists (may be needed too...)
        ArrayList<String> errs = new ArrayList<String>();
        int curStrIdx = 0;
    }
    
    public ExecFiPaDat[] getExecFiPaDatFor(Executable x, ConfigData cd, Integer lineNoHint, char kind){

        // 1. analyse the number/size of lists which are matching the current scope(s)
        String[] lists = getTestStringArrayFor(x, cd, kind);  // get the lists which match the current scope(s)
        ArrayList<Integer> numParamListsPerDataSet = new ArrayList<Integer>();
        int retArrLen = 0; 
        for (String s:lists)
            if (s.equals("\n")) 
                retArrLen++;
            else {
                while (numParamListsPerDataSet.size()<=retArrLen) numParamListsPerDataSet.add(null);
                Integer i = numParamListsPerDataSet.get(retArrLen);
                numParamListsPerDataSet.set(retArrLen, i==null ? (kind=='f'?0:1) : (i+1)); // for fields we start with 0, because we must not count the field name list!
            }
        if (kind!='f' && retArrLen==0)
            retArrLen=lists.length==0 ? 0 : 1;    // default for ranges and values  (not fields)
            
        ExecFiPaDat[] r = new ExecFiPaDat[retArrLen];
        if (retArrLen==0)
            return r;   // no matching list found
            
        // 2. analyse the lists themself and store the results in the returned array
        String kindName = kind=='v' ? "values" : (kind=='r' ? "range" : (kind=='f' ? "fields" : "value"));
        int curIdx = 0;
        int curKdx = 0;
        int numF = 0;                               // may differ...
        int numP = x.getParameterTypes().length;    // constant for al tests of x
        String huSignat = calcSignature(x, true);
        for (String s:lists){
            if (r[curIdx]==null){    // we start a new data set
                ExecFiPaDat d = r[curIdx] = new ExecFiPaDat();
                if (kind=='f'){ // first line is a special one containing the fields names
                    d.fiNamArr = splitValueList(s); // parse s to a string array (the names of the used fields)
                    numF = d.fiNamArr.length;
                    d.fiTypArr = new Class[numF];
                    for (int i=0; i<numF; i++){
                        String nam = d.fiNamArr[i].trim();
                        d.fiNamArr[i] = nam; // store trimmed version
                        Field f = refChkF.get(nam);
                        if (f==null)
                            d.errs.add("ERROR: Found bad fields definition for "+huSignat+" (unknown field name \""+nam+"\" in first string) in: "
                                    +lnhAnnot(lineNoHint));
                        else
                            d.fiTypArr[i] = f.getType();  
                    }
                }
                else {
                    d.fiNamArr = new String[0];
                    numF = 0;
                    d.fiTypArr = new Class[0];
                    
                }
                d.paTypArr = x.getParameterTypes();
                d.fpTypArr = concat(d.fiTypArr, d.paTypArr);
                
                d.fiStrArr = new String[numParamListsPerDataSet.get(curIdx)];
                d.paStrArr = new String[numParamListsPerDataSet.get(curIdx)];
                d.fpStrArr = new String[numParamListsPerDataSet.get(curIdx)];
                curKdx = 0;
                if (kind=='f')
                    continue;   // else we still have to handle s, because it usually includes parameter values
            }
            
            if (s.equals("\n")){        // we finish the current data set 
                curIdx++;               // -> this will start the next data set (if there is one)
                continue; 
            }
            
            // "normal case": a new list with values for fields and parameters -> stored in fpStrArr directly or splited per arraycopy
            String[] pa = splitValueList(s);
            if (pa.length!=numF+numP)
                r[curIdx].errs.add("ERROR: Found bad "+kindName+" definition for "+huSignat+" (bad list length "+pa.length+", should be "
                                          +(numF+numP)+") in: "+s+lnhAnnot(lineNoHint)); 
            else {
                r[curIdx].fiStrArr[curKdx] = toStr(pa,    0,      numF, ",");
                r[curIdx].paStrArr[curKdx] = toStr(pa, numF, pa.length, ",");
                r[curIdx].fpStrArr[curKdx] = s;
            }
            curKdx++;
            try {
                /* Object[] paArr = */ objArrFromParamStr(r[curIdx].fpTypArr, s);
                // was obviously ok ;-)
            }
            catch (Exception e){
                r[curIdx].errs.add("ERROR: Found bad "+kindName+" definition for "+huSignat+" in: "+s+lnhAnnot(lineNoHint)+"\n"+e); 
            }
        }
        return r;
    }

    boolean classOk(){
        if (clsIs==null || clsIs==classState.unchecked){ // first call -> we check it now
            // 1. check if a java file with this name exists, else the compilation is not possible! 
            if (! new File(clsNam +".java").exists()){
                clsIs = classState.noFile;
                return false;   
            }
        
            // 2. check if class exists, else the method tests can not be performed
            try {
                stuSol = Class.forName(clsNam);
		    } catch (ClassNotFoundException e) {
			    clsIs = classState.notCompiled;
                return false;   
            }
            
            // 3. store the state (that the Java-File exists and the complied class file also)
            clsIs=classState.compiled; // stuSol can be used
        }
        return clsIs==classState.compiled;
    }

    boolean hasElems(){ // elements like fields (variables), methods and constructors are called accessible elements
                        //  https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/AccessibleObject.html
        if (!classOk())
            return false;
            
        if (elmIs==null || elmIs==elemsState.unchecked){ // first call -> we try now to load the methods, fields and constructors
            
            // 1. load all elements of the students solution into stuAllE and stuAllF
            Method[] sma = stuSol.getDeclaredMethods();
            for (Method m:sma)
                stuAllE.put(calcSignature(m), m); 

            Constructor[] sca = stuSol.getDeclaredConstructors();
            for (Constructor c:sca)
                stuAllE.put(calcSignature(c), c);   // false: not for humans 
            
            Class tmpCls = stuSol;
            do {
                Field[] sfa = tmpCls.getDeclaredFields();
                for (Field f:sfa)
                    stuAllF.put(f.getName(), f);     
                tmpCls = tmpCls.getSuperclass();
            } while(!tmpCls.equals(Object.class));


            Field[]  sfa = stuSol.getDeclaredFields();
            for (Field f:sfa)
                stuAllF.put(f.getName(), f); 

            // 2. store the state (that we filled stuAllE, stuAllF, allStuC)
            elmIs=elemsState.loaded;
        }
        return elmIs==elemsState.loaded;
    }

    public String toString(){
        return "Check("+clsNam+", "+refSol.getName()+")\n"
             + "    clsIs : "+clsIs+"\n"
             + "    elmIs : "+elmIs+"\n"
             + "    "+stuAllE.size()+" meth/constr found in "              +clsNam          +":"+setToDbgLines(stuAllE.keySet())+"\n"
             + "    "+refChkE.size()+" meth/constr to be checked found in "+refSol.getName()+":"+setToDbgLines(refChkE.keySet())+"\n"
             + "    "+stuAllF.size()+" fields      found in "              +clsNam          +":"+setToDbgLines(stuAllF.keySet())+"\n"
             + "    "+refChkF.size()+" fields      to be checked found in "+refSol.getName()+":"+setToDbgLines(refChkF.keySet())+"\n";
    }
    
    // ------------------------ generation of common error texts ----------------------------------------
    
    public String hasElems_errorText(ContextInfo ctx){
        switch(clsIs){
            case noFile     : if (false) // (gaveMsg_noFile) 
                                  return "";
                              gaveMsg_noFile = true;
                              return "Die Datei \""+ clsNam +".java\" (mit der Klasse "+clsNam+") konnte nicht gefunden werden."
                                   +" Deshalb schlägt dieser Test ("+ctx.getContext()+") fehl!";  
                              
            case notCompiled: if (false) // (gaveMsg_notCompiled) 
                                  return "";
                              gaveMsg_notCompiled = true;
                              return "Die Klasse \""+ clsNam +"\" konnte nicht kompiliert werden."
                                   +" Deshalb schlägt dieser Test ("+ctx.getContext()+") fehl!";  
        }
        return "Test ("+ctx.getContext()+") konnte nicht durchgeführt werden.";
    }

    public String fieldNotFound_errorText(ContextInfo ctx){
        return "Die Variable "+ctx.signature+" konnte in der Klasse "+clsNam+" nicht gefunden werden."
             +" Deshalb schlägt dieser Test ("+ctx.typeOfTest+") fehl!";  
    }

    public String fieldNotFound_errorText(ContextInfo ctx, String fieldName){
        return "Die Variable "+fieldName+" konnte in der Klasse "+clsNam+" nicht beim Test von "+ctx.signature+" gefunden werden."
             +" Deshalb schlägt dieser Test ("+ctx.typeOfTest+") fehl!";  
    }

    public String executableNotFound_errorText(ContextInfo ctx){
        return ctx.getStr_the_kindOfMember()+" "+ctx.signature+" konnte in der Klasse "+clsNam+" nicht gefunden werden."
             +" Deshalb schlägt dieser Test ("+ctx.typeOfTest+") fehl!";  
    }

    public String fieldValueDiffers_errorText(String name, String signature, Object[] paArr, Object valRefSol, Object valStuSol, ConfigData cd){
        double floatEps = cd==null || cd.floatEps==null ? 0 : cd.floatEps;
        if (floatEps!=0 && (isFloating(valRefSol) || isFloating(valStuSol))){ // round values according to epsilon value
            valRefSol = epsRoundIfFloating(valRefSol, floatEps); 
            valStuSol = epsRoundIfFloating(valStuSol, floatEps);
        }
        return "Der Aufruf von "+clsSignParaList(clsNam, signature, paArr)
              +" hat einen falschen Variablenwert in "+clsNam+"."+name+" erzeugt. Er hätte "+asString(valRefSol, cd) +" sein müssen, war aber: "+asString(valStuSol, cd);
    }
    
    public String fieldValueDiffers_errorText(String name, Object valRefSol, Object valStuSol, ConfigData cd){
        double floatEps = cd==null || cd.floatEps==null ? 0 : cd.floatEps;
        if (floatEps!=0 && (isFloating(valRefSol) || isFloating(valStuSol))){ // round values according to epsilon value
            valRefSol = epsRoundIfFloating(valRefSol, floatEps); 
            valStuSol = epsRoundIfFloating(valStuSol, floatEps);
        }
        return "Der Wert in "+clsNam+"."+name+" hätte "+asString(valRefSol, cd) +" sein müssen, war aber: "+asString(valStuSol, cd);
    }
    
    public String clsSignParaList(String clsNam, String signature, Object[] paArr){
        return (signature.indexOf(clsNam)==0 && signature.charAt(clsNam.length())=='(' ? "" : (clsNam+".")) // skip class name for constructors
               +signature+(paArr.length==0?"":" mit der Parameterliste "+objParamList(paArr));
    }
    
    public String returnValueDiffers_errorText(String signature, String fiValHint, Object[] paArr, Object retRefSol, Object retStuSol, ConfigData cd){
        double floatEps = cd==null || cd.floatEps==null ? 0 : cd.floatEps;
        if (floatEps!=0 && (isFloating(retRefSol) || isFloating(retStuSol))){ // round values according to epsilon value
            retRefSol = epsRoundIfFloating(retRefSol, floatEps); 
            retStuSol = epsRoundIfFloating(retStuSol, floatEps);
        }
        return "Der Aufruf von "+clsSignParaList(clsNam, signature, paArr)
              +(fiValHint==null?"":(" und den aktuellen Werten von "+fiValHint)) +" hätte "+ ( isConstructor(signature)
                 ? ("in " + asString(retRefSol, cd)+" erzeugen müssen, erzeugte aber: "+asString(retStuSol, cd))         // better formulation if class vars are involved
                 : (        asString(retRefSol, cd)+" zurückgeben müssen, lieferte aber: "+asString(retStuSol, cd))
               );
    }
    
    public String badReturnValue_errorText(String signature, Object[] paArr, String reason, Object retStuSol, ConfigData cd){
        double floatEps = cd==null || cd.floatEps==null ? 0 : cd.floatEps;
        if (floatEps!=0 && isFloating(retStuSol)) // round values according to epsilon value
            retStuSol = epsRoundIfFloating(retStuSol, floatEps);
        return "Der Aufruf von "+clsSignParaList(clsNam, signature, paArr)+" lieferte "+asString(retStuSol, cd)
             +", dies ist falsch"+(reason==null||reason.trim().equals("") ? "." : (", denn: "+reason));
    }
    
    public String systemOutDiffers_errorText(String signature, String fiValHint, Object[] paArr, String retRefSol, String retStuSol, ConfigData cd){
        // TODO? could try to parse to floats... and use eps like in returnValueDiffers_errorText
        // double floatEps = cd==null || cd.floatEps==null ? 0 : cd.floatEps;
        // if (floatEps!=0 && (isFloating(retRefSol) || isFloating(retStuSol))){ // round values according to epsilon value
        //     retRefSol = epsRoundIfFloating(retRefSol, floatEps); 
        //     retStuSol = epsRoundIfFloating(retStuSol, floatEps);
        // }
        return "Der Aufruf von "+clsSignParaList(clsNam, signature, paArr)
              +(fiValHint==null?"":(" und den aktuellen Werten von "+fiValHint))
              +" hätte "+asString(retRefSol, cd)+" ausgeben müssen, die Ausgabe per System.out war aber: "+asString(retStuSol, cd);
    }
    
    public String exceptionDuringExecution_errorText(String signature, Object[] paArr, Throwable e, ConfigData cd){
        Throwable cause = e.getCause(); // often e is an InvocationTargetException which wraps the underlying exception that describes the real reason!
        if (cause==null)
            cause=e;        // ok, it was not wrapped:-)
        return "Der Aufruf von "+clsSignParaList(clsNam, signature, paArr)+" führte zu einem Programmabbruch!\n"
                + cause + "\nsiehe hier:\n" + formatedStackTrace(cause); 
    }
    
    public String hintOther(Variants mv){
        String hintOther = "";
        if (mv!=null){
            Class[] ot = mv.otherTypes();
            if (ot!=null && ot.length>0){
                hintOther = " (oder wenigstens "+ot[0];
                for (int i=1; i<ot.length; i++)
                    hintOther+=(i+1<ot.length ? ", ":" bzw. ")+ot[i];
                hintOther += ")";
            }
        }
        return hintOther;
    }
    
    public String badParamValues_errorText(String signature, Object[] paArrOri, Object[] paArrRef, Object[] paArrStu, ConfigData cd){
    	return "Der Aufruf von "+clsSignParaList(clsNam, signature, paArrOri)+" führte zu falschen Werten in der Parameterliste."
             +" Erwartet wurden folgende Werte: "+asString(paArrRef, cd) +", aber gefunden wurden "+asString(paArrStu, cd);
    }
    
    public String wrongReturnType_errorText(String signature, String contextName, Class<?> mrRt, Variants mv, Class<?> msRt){ // should be never called for a constructor
        return "Die Methode "+signature+" in der Klasse "+clsNam+" sollte den Rückgabetyp "+humanStr4JavaType(mrRt)+hintOther(mv)+" haben. Sie hat aber den Rückgabetyp "+humanStr4JavaType(msRt)+"!"
             +" Deshalb schlägt dieser Test ("+contextName+") fehl!"; 
    }
    
    public String wrongVariableType_errorText(String name, String contextName, Class<?> frTy, Variants mv, Class<?> fsTy){
        return "Die Variable "+name+" in der Klasse "+clsNam+" sollte den Typ "+humanStr4JavaType(frTy)+hintOther(mv)+" haben. Sie hat aber den Typ "+humanStr4JavaType(fsTy)+"!"
             +" Deshalb schlägt dieser Test ("+contextName+") fehl!"; 
    }
    
    public String wrongModifierType_errorText(String signature, String contextName, String memberTypeName, ModifierCheck mc, int modif){
        String found = mc.isDe(modif);
        if (found.equals(""))
            found = "kein entsprechender Modifier"; // found = "\"\"";
        return memberTypeName+" "+signature+" in der Klasse "+clsNam+" sollte "+mc.shouldBeDe()+" sein. Es wurde aber "+found+" gefunden!" // . Sie ist es aber nicht, sondern "+found+"!" (komisch bei !static)
             +" Deshalb schlägt dieser Test ("+contextName+") fehl!"; 
    }

    public String wrongModifierType_errorText(String signature, String contextName, String memberTypeName, int modifRef, int modifStu){
        String found    = ModifierCheck.isDeStatic(modifStu, modifRef); 
        String shouldBe = ModifierCheck.isDeStatic(modifRef, modifStu); 
        if (found.equals(""))
            found = "kein entsprechender Modifier"; 
        return memberTypeName+" "+signature+" in der Klasse "+clsNam+" sollte "+shouldBe+" sein. Es wurde aber "+found+" gefunden!" // . Sie ist es aber nicht, sondern "+found+"!" (komisch bei !static)
             +" Deshalb schlägt dieser Test ("+contextName+") fehl!"; 
    }

    public String badUseOfLoops_errorText(String signature, String contextName, boolean mustHave, String[] loopTypes){
        String demanded = (loopTypes==null || loopTypes.length==0)
                        ? ""
                        : (" "+variantsArrayToList(loopTypes, "", ", ", " oder "));

        return "Die Methode "+signature+" in der Klasse "+clsNam+" sollte "+(mustHave ? "mindestens eine" : "keine" )+demanded+" Schleife nutzen."
             +" Sie tat es aber"+(mustHave ? "nicht":"")+"!"
             +" Deshalb schlägt dieser Test ("+contextName+") fehl!"; 
    } 

    public String badUseOfKey_errorText(String signature, String contextName, boolean mustHave, String demanded){
        return "Die Methode "+signature+" in der Klasse "+clsNam+" sollte "+demanded+(mustHave ? " unbedingt" : " NICHT")+" nutzen."
             +" Sie tat es aber"+(mustHave ? " nicht":"")+"!"
             +" Deshalb schlägt dieser Test ("+contextName+") fehl!"; 
    } 

    public String dbg_catchInfoInvoke(String signature, String paS, Object obj, Object[] paA, Exception e){
        Throwable cause = e.getCause(); // often e is an InvocationTargetException which wraps the underlying exception that describes the real reason!
        if (cause==null)
            cause=e;        // ok, it was not wrapped:-)
        return "Check.checkReturnValue("+signature+") tested("+paS+") invoked with (obj:"+obj+", paLi:"+objParamList(paA)+") catched:"
               + e + "\n" + formatedStackTrace(cause);
    }
    
    public String formatedStackTrace(Throwable e){
        StringBuilder r = new StringBuilder();
        for (StackTraceElement t : e.getStackTrace())
            if (clsNam.equals(t.getClassName()))
                r.append("         "+t.toString().replaceAll("^app//", "")).append("\n");  // for timeouts we had e.g.: "app//Berechnungen.sumOf(Berechnungen.java:59)" -> remove "app//" at begin

        return r.toString();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //   
    //  Methods for certain tests
    // 

    public String[] getTestParamsArrayFor(Executable x, ConfigData cd){
        return getTestStringArrayFor(x, cd, 'v');
    }
    
    public String[] getTestRangesArrayFor(Executable x, ConfigData cd){
        return getTestStringArrayFor(x, cd, 'r');
    }

    public String[] getTestStringArrayFor(Executable x, ConfigData cd, char kind){ // kind: 'r'=ranges; 'f'=fields; 'v'=values (or if not 'r'/'f')
        
        // TestParamSets sets = m.getAnnotation(TestParamSets.class);
        // if (sets==null){ // no repetition -> at most 1 set -> trivial case (old style of storing test parameters)
        //     TestParamSet a = m.getAnnotation(TestParamSet.class);
        //     return a==null ? new String[0] : (ranges ? a.ranges() : a.values());
        // }
        
        TestParamSet[] tePaSetArr = getTestParamSetArrFor(x);

        // When cd has no scope -> use all sets, else only those which have at least one scope in common.
        ArrayList<String> li = new ArrayList<String>();
        for (TestParamSet a : tePaSetArr)   // sets.value())
            if (cd==null || !cd.hasScope() || hasSameElement(a.scopes(), cd.scopes)){
                ArrayList<String> tmp = new ArrayList<String>();
                for (String s:(kind=='r' ? a.ranges() : (kind=='f' ? a.fields() : a.values())))
                    tmp.add(s);
                li.addAll(tmp);                
                if (kind=='f' && tmp.size()>0)
                    li.add("\n");
            }
        String[] ret = new String[li.size()];
        for (int i=0; i<li.size(); i++)
            ret[i] = li.get(i);
        return ret;
    }
        
    public static Pattern pat_signatureTrafo = Pattern.compile("^(.*?)\\((.*?)\\)$");
    public        String signatureTrafoS(String s){ return signatureTrafo(s, false); }
    public        String signatureTrafoR(String s){ return signatureTrafo(s, true ); }
    public static String signatureNormalization(String s){  // this is a human readable kind of normalization that ensures that we can use signatures as keys in human readable maps
                                                            // -> we remove spaces, write java type names as humans would encode them  ...
        Matcher m = pat_signatureTrafo.matcher(s.trim());
        if (m.matches()){
            String r="";
            for (String pTy : m.group(2).split(","))
                r+=(r.length()==0 ? "" : ",")+signatureParamTypeTrafo_java2human(pTy.trim());
            return m.group(1)+"("+r+")";
        }
        return s;
    }
    public static String nameFromSignature(String s){
        Matcher m = pat_signatureTrafo.matcher(s.trim());
        return m.matches() ? m.group(1) : s;
    }
    public String signatureTrafo(String s, boolean isR){ // this is a machine readable kind of normalization that ensures that we can use signatures as keys in our maps etc. 
                                                         // -> we remove spaces, write java type names as java encodes them  ...
        Matcher m = pat_signatureTrafo.matcher(s.trim());
        if (m.matches()){
            String r="";
            for (String pTy : m.group(2).split(","))
                r+=(r.length()==0 ? "" : ",")+signatureParamTypeTrafo_human2Java(pTy.trim());
            return m.group(1)+(isR&&m.group(1).equals(clsNam)?"_refSolClass":"")+"("+r+")";
        }
        return s;
    }
    
    public static TreeMap<String, String> signatureMap_h2j = new TreeMap<String, String>();
    public static TreeMap<String, String> signatureMap_j2h = new TreeMap<String, String>();
    public static void signatureParamTypeTrafo_init(){ 
        
        int maxDim = 5; // maxDim==5 -> we support nice names for 1 dimensional arrays ( e.g. int[] ) up to 5 dimensional arrays ( e.g. int[][][][][] ) 
        Class<?>[] allTy = { byte.class, short.class, int    .class, long.class, float.class, double.class, boolean.class, char     .class 
                           , Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class, Boolean.class, Character.class, String.class }; 
        for (Class<?> t : allTy){                   // e.g. long or Long
            String j = ""+t;                        // e.g. long or class java.lang.Long
            String h = humanStr4JavaType(t);        // e.g. long or Long
            signatureMap_h2j.put(h, j);  
            signatureMap_j2h.put(j, h);
            for (int i=1; i<=maxDim; i++){
                t =  componentType_to_arrayType(t); // e.g. long[][].class or java.lang.Long[][].class  for i==2
                j =  ""+t;                          // e.g. class [[J      or class[[Ljava.lang.Long    for i==2
                h += "[]";                          // e.g. long[][]       or Long[][]                  for i==2
                signatureMap_h2j.put(h, j);  
                signatureMap_j2h.put(j, h);
            }
        }
    }
    
    public static Class<?> componentType_to_arrayType(Class<?> eTy){
        return Array.newInstance(eTy, 0).getClass();
    }
    
    public static String humanStr4JavaType(Class<?> ty){
        // handle arrays
        if (ty.isArray())
            return humanStr4JavaType(ty.getComponentType())+"[]";   // may be a recursion (depth == dimension of the array)
    
        // primitives are ok. We add typical Java classes, else we would have to define e.g. add(java.lang.Double,java.lang.Double) instead of simply add(Double,Double)
        if (ty==    String.class) return    "String";
        if (ty== Character.class) return "Character";
        if (ty==    Double.class) return    "Double";
        if (ty==     Float.class) return     "Float";
        if (ty==      Byte.class) return      "Byte";
        if (ty==     Short.class) return     "Short";
        if (ty==   Integer.class) return   "Integer";
        if (ty==      Long.class) return      "Long";
        if (ty==   Boolean.class) return   "Boolean";
        // add further if needed ...

//         if (ty==      char.class) return      "char";
//         if (ty==    double.class) return    "double";
//         if (ty==     float.class) return     "float";
//         if (ty==      byte.class) return      "byte";
//         if (ty==     short.class) return     "short";
//         if (ty==       int.class) return       "int";
//         if (ty==      long.class) return      "long";
//         if (ty==   boolean.class) return   "boolean";

        return ""+ty; // rest may be ok, e.g. primitives or classes from the default package
    }
//             case        "String" : return ""+       String.class;
//             case     "Character" : return ""+    Character.class;
//             case        "Double" : return ""+       Double.class;
//             case         "Float" : return ""+        Float.class;
//             case          "Byte" : return ""+         Byte.class;
//             case         "Short" : return ""+        Short.class;
//             case       "Integer" : return ""+      Integer.class;
//             case          "Long" : return ""+         Long.class;
//             case       "Boolean" : return ""+      Boolean.class;
//             
//             case      "String[]" : return ""+     String[].class;
//             case   "Character[]" : return ""+  Character[].class;
//             case      "Double[]" : return ""+     Double[].class;
//             case       "Float[]" : return ""+      Float[].class;
//             case        "Byte[]" : return ""+       Byte[].class;
//             case       "Short[]" : return ""+      Short[].class;
//             case     "Integer[]" : return ""+    Integer[].class;
//             case        "Long[]" : return ""+       Long[].class;
//             case     "Boolean[]" : return ""+    Boolean[].class;
//             
//             case        "char[]" : return ""+       char[].class;
//             case      "double[]" : return ""+     double[].class;
//             case       "float[]" : return ""+      float[].class;
//             case        "byte[]" : return ""+       byte[].class;
//             case       "short[]" : return ""+      short[].class;
//             case         "int[]" : return ""+        int[].class;
//             case        "long[]" : return ""+       long[].class;
//             case     "boolean[]" : return ""+    boolean[].class;
// 
//             case    "String[][]" : return ""+   String[][].class;
//             case "Character[][]" : return ""+Character[][].class;
//             case    "Double[][]" : return ""+   Double[][].class;
//             case     "Float[][]" : return ""+    Float[][].class;
//             case      "Byte[][]" : return ""+     Byte[][].class;
//             case     "Short[][]" : return ""+    Short[][].class;
//             case   "Integer[][]" : return ""+  Integer[][].class;
//             case      "Long[][]" : return ""+     Long[][].class;
//             case   "Boolean[][]" : return ""+  Boolean[][].class;
//             
//             case      "char[][]" : return ""+     char[][].class;
//             case    "double[][]" : return ""+   double[][].class;
//             case     "float[][]" : return ""+    float[][].class;
//             case      "byte[][]" : return ""+     byte[][].class;
//             case     "short[][]" : return ""+    short[][].class;
//             case       "int[][]" : return ""+      int[][].class;
//             case      "long[][]" : return ""+     long[][].class;
//             case   "boolean[][]" : return ""+  boolean[][].class;

    public static String signatureParamTypeTrafo_human2Java(String s){ // important e.g. for signatures like add(Double,Double)
        if (signatureMap_h2j.size()==0)
            signatureParamTypeTrafo_init();
        String ret = signatureMap_h2j.get(s);
        return ret==null ? s : ret;
    }
    
    public static String signatureParamTypeTrafo_java2human(String s){ // important e.g. for signatures like add(Double,Double)
        if (signatureMap_j2h.size()==0)
            signatureParamTypeTrafo_init();
        String ret = signatureMap_j2h.get(s);
        return ret==null ? s : ret;
    }
        
    public void addToResultList(Result r){
        resultList.add(r);   
    }    
    
    public String get_results_junitStyle(){
        return get_results_junitStyle(true);
    }
    public String get_results_junitStyle(boolean setEndTimeIfNotDoneYet){
        if (setEndTimeIfNotDoneYet && timeMs_end<=0)
            timeMs_end = System.currentTimeMillis();
            
        int numChks = resultList.size();
        StringBuilder ret = new StringBuilder();
        
        // 0) handle special case that there was no test (JUnit makes it to one test which fails!)
        ArrayList<Result> resultList = numChks==0 
                                     ? new ArrayList<>(Arrays.asList(new Result(false, null, "initializationError()\njava.lang.Exception: No runnable methods")))   // we use a temp. pseudo list
                                     : this.resultList; // can iterate over the valid list 
        
        // 1) add the progress string like ..E..E.E.
        int errNo=0;
        for (Result r: resultList){
            if (r.hasError())
                errNo++;
            ret.append(r.hasError() ? ".E" : ".");
        }
        
        // 2) add the used time and number of failures 
        ret.append("\nTime: "+((timeMs_end<=0 ? System.currentTimeMillis() : timeMs_end)-timeMs_start)/1000.0);
        if (errNo>0)
            ret.append(errNo==1 ? "\nThere was 1 failure:"
                                : "\nThere were "+errNo+" failures:");

        // 3) show single error info and count errors 
        errNo=0;
        for (Result r: resultList)
            if (r.hasError()){
                errNo++;
                ret.append("\n").append(errNo).append(") ").append(r.getTestName())
                   .append("\njava.lang.AssertionError: ").append(r.getInfo())
                   .append("\n\t ").append(r.getPosition())
                   .append("\n\tat org.junit\n"); // the current CodeOcean_JUnitOutputParser uses this line as separator (end of error info)
            }
            
        // 4) sum up the results
        if (errNo==0)
            ret.append("\n\n").append("OK (").append(numChks).append(" tests)");
        else
            ret.append("\n\n").append("FAILURES!!!\nTests run: ").append(numChks).append(",  Failures: ").append(errNo);
        
        return ret.toString();
    }
    
    public void printOut_results_junitStyle(){
        System.out.println(get_results_junitStyle());
    }
        
    public Result checkCheck(Check c, String name, String type){ 
        return checkCheck(c, name, type, null);
    }
    
    public Result checkCheck(Check c, String name, String type, ConfigData cd){ 
        ContextInfo ctx = new ContextInfo(name, type);
        cd = merge(cd);
        if (cd.dbg(1)) sopl("starting checkCheck("+name+", "+type+", "+cd+")");
        
        StringBuilder sumResults = new StringBuilder();
        for (Result r: c.resultList)
            if (r.hasError()) {
            	addToResultList(r);
            	return r;
            }
            else
            	sumResults.append(r.getInfo());
        return new Result(true, ctx, "alles ok:\n"+sumResults.toString()); 
    }
    
    public Result checkFieldValue(String name){ 
        return checkFieldValue(name, null);
    }
    
    public Result checkFieldValue(String name, ConfigData cd){ 
        ContextInfo ctx = new ContextInfo(name, "Werttest:Variable");
        cd = merge(cd);
        if (cd.dbg(1)) sopl("starting checkFieldValue("+name+", "+cd+")");
        
        if (!hasElems())     // check if stuSol can be used
            return new Result(false, ctx, hasElems_errorText(ctx));
        
        Field fs = stuAllF.get(name);
        if (fs==null)
            return new Result(false, ctx, fieldNotFound_errorText(ctx));

        Field fr = refChkF.get(name);
        if (fr==null)   // not to be checked -> existing type in ms is correct by default!!!!
            return new Result(true, ctx, "ok, keine zu testenden Vorgaben zur Variable "+name+" gefunden"); 
        
        Object objR = objIfNeeded(refSol, fr); // objR must be != null for non-static fields; for static ones it could be null too
        Object objS = objIfNeeded(stuSol, fs); // objS must be != null for non-static fields; for static ones it could be null too

        Object valR = null; try { valR = fr.get(objR); } catch (Exception e){} // e.g. IllegalAccessException.
        Object valS = null; try { valS = fs.get(objS); } catch (Exception e){}
        
        if (!isEqual(valR, valS, cd)){ // IMPORTANT: note that  Long(0) != Integer(0)
            if (cd.dbg(2)) sopl("R("+ (valR==null ? "nullType" : humanStr4JavaType(valR.getClass()))
                             +") S("+ (valS==null ? "nullType" : humanStr4JavaType(valS.getClass()))+"):"+asString(valR)+"  <=!=>  "+asString(valS)+" -> "+valR.equals(valS));
            return new Result(false, ctx, fieldValueDiffers_errorText(name, valR, valS, cd));
        }        
        return new Result(true, ctx, "ok, die Variable "+name+" hat diesen Werttest bestanden ("+valR+" == "+valS+")"); 
    }
    
    // 2^6-1 = 63 combinations (not 6*false)                                                 // P      S   T=retTy V=retVal O=out F=fields      M=P&S
    public Result checkExecM    (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true, false, false, false, false}); }
    public Result checkExecP    (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true, false, false, false, false, false}); }
    public Result checkExecS    (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false,  true, false, false, false, false}); }
    public Result checkExecT    (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false, false,  true, false, false, false}); }
    public Result checkExecV    (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false, false, false,  true, false, false}); }
    public Result checkExecO    (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false, false, false, false,  true, false}); }
    public Result checkExecF    (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false, false, false, false, false,  true}); }
    public Result checkExecMT   (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true,  true, false, false, false}); }
    public Result checkExecPT   (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true, false,  true, false, false, false}); }
    public Result checkExecST   (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false,  true,  true, false, false, false}); }
    public Result checkExecMV   (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true, false,  true, false, false}); }
    public Result checkExecPV   (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true, false, false,  true, false, false}); }
    public Result checkExecSV   (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false,  true, false,  true, false, false}); }
    public Result checkExecMO   (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true, false, false,  true, false}); }
    public Result checkExecPO   (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true, false, false, false,  true, false}); }
    public Result checkExecSO   (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false,  true, false, false,  true, false}); }
    public Result checkExecTV   (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false, false,  true,  true, false, false}); }
    public Result checkExecTO   (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false, false,  true, false,  true, false}); }
    public Result checkExecVO   (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false, false, false,  true,  true, false}); }
    public Result checkExecMTV  (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true,  true,  true, false, false}); }
    public Result checkExecPTV  (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true, false,  true,  true, false, false}); }
    public Result checkExecSTV  (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false,  true,  true,  true, false, false}); }
    public Result checkExecMTO  (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true,  true, false,  true, false}); }
    public Result checkExecPTO  (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true, false,  true, false,  true, false}); }
    public Result checkExecSTO  (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false,  true,  true, false,  true, false}); }
    public Result checkExecMVO  (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true, false,  true,  true, false}); }
    public Result checkExecPVO  (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true, false, false,  true,  true, false}); }
    public Result checkExecSVO  (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false,  true, false,  true,  true, false}); }
    public Result checkExecTVO  (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false, false,  true,  true,  true, false}); }
    public Result checkExecMTVO (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true,  true,  true,  true, false}); }
    public Result checkExecPTVO (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true, false,  true,  true,  true, false}); }
    public Result checkExecSTVO (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false,  true,  true,  true,  true, false}); }
    public Result checkExecMF   (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true, false, false, false,  true}); }
    public Result checkExecPF   (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true, false, false, false, false,  true}); }
    public Result checkExecSF   (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false,  true, false, false, false,  true}); }
    public Result checkExecTF   (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false, false,  true, false, false,  true}); }
    public Result checkExecVF   (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false, false, false,  true, false,  true}); }
    public Result checkExecOF   (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false, false, false, false,  true,  true}); }
    public Result checkExecMTF  (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true,  true, false, false,  true}); }
    public Result checkExecPTF  (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true, false,  true, false, false,  true}); }
    public Result checkExecSTF  (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false,  true,  true, false, false,  true}); }
    public Result checkExecMVF  (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true, false,  true, false,  true}); }
    public Result checkExecPVF  (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true, false, false,  true, false,  true}); }
    public Result checkExecSVF  (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false,  true, false,  true, false,  true}); }
    public Result checkExecMOF  (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true, false, false,  true,  true}); }
    public Result checkExecPOF  (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true, false, false, false,  true,  true}); }
    public Result checkExecSOF  (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false,  true, false, false,  true,  true}); }
    public Result checkExecTVF  (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false, false,  true,  true, false,  true}); }
    public Result checkExecTOF  (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false, false,  true, false,  true,  true}); }
    public Result checkExecVOF  (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false, false, false,  true,  true,  true}); }
    public Result checkExecMTVF (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true,  true,  true, false,  true}); }
    public Result checkExecPTVF (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true, false,  true,  true, false,  true}); }
    public Result checkExecSTVF (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false,  true,  true,  true, false,  true}); }
    public Result checkExecMTOF (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true,  true, false,  true,  true}); }
    public Result checkExecPTOF (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true, false,  true, false,  true,  true}); }
    public Result checkExecSTOF (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false,  true,  true, false,  true,  true}); }
    public Result checkExecMVOF (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true, false,  true,  true,  true}); }
    public Result checkExecPVOF (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true, false, false,  true,  true,  true}); }
    public Result checkExecSVOF (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false,  true, false,  true,  true,  true}); }
    public Result checkExecTVOF (String signature){ return checkExecMTVOF(signature, null, new boolean[] {false, false,  true,  true,  true,  true}); }
    public Result checkExecMTVOF(String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true,  true,  true,  true,  true}); }
    public Result checkExecPTVOF(String signature){ return checkExecMTVOF(signature, null, new boolean[] { true, false,  true,  true,  true,  true}); }
    public Result checkExecSTVOF(String signature){ return checkExecMTVOF(signature, null, new boolean[] {false,  true,  true,  true,  true,  true}); }

    // could also define 16 aliases for each method with M in the signature (M->PS), e.g.
    // public Result checkExecPS    (String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true, false, false, false, false}); }
    // ...
    // public Result checkExecPSTVOF(String signature){ return checkExecMTVOF(signature, null, new boolean[] { true,  true,  true,  true,  true,  true}); }
    

    // 2^6-1 = 63 combinations (not 6*false)                                                                        //   PPP   Stat  retTy retVal   Out   Fields=00P   M=P&S
    public Result checkExecM    (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true,  true, false, false, false, false}); }
    public Result checkExecP    (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true, false, false, false, false, false}); }
    public Result checkExecS    (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false,  true, false, false, false, false}); }
    public Result checkExecT    (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false, false,  true, false, false, false}); }
    public Result checkExecV    (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false, false, false,  true, false, false}); }
    public Result checkExecO    (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false, false, false, false,  true, false}); }
    public Result checkExecF    (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false, false, false, false, false,  true}); }
    public Result checkExecMT   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true,  true,  true, false, false, false}); }
    public Result checkExecPT   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true, false,  true, false, false, false}); }
    public Result checkExecST   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false,  true,  true, false, false, false}); }
    public Result checkExecMV   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true,  true, false,  true, false, false}); }
    public Result checkExecPV   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true, false, false,  true, false, false}); }
    public Result checkExecSV   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false,  true, false,  true, false, false}); }
    public Result checkExecMO   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true,  true, false, false,  true, false}); }
    public Result checkExecPO   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true, false, false, false,  true, false}); }
    public Result checkExecSO   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false,  true, false, false,  true, false}); }
    public Result checkExecTV   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false, false,  true,  true, false, false}); }
    public Result checkExecTO   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false, false,  true, false,  true, false}); }
    public Result checkExecVO   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false, false, false,  true,  true, false}); }
    public Result checkExecMTV  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true,  true,  true,  true, false, false}); }
    public Result checkExecPTV  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true, false,  true,  true, false, false}); }
    public Result checkExecSTV  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false,  true,  true,  true, false, false}); }
    public Result checkExecMTO  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true,  true,  true, false,  true, false}); }
    public Result checkExecPTO  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true, false,  true, false,  true, false}); }
    public Result checkExecSTO  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false,  true,  true, false,  true, false}); }
    public Result checkExecMVO  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true,  true, false,  true,  true, false}); }
    public Result checkExecPVO  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true, false, false,  true,  true, false}); }
    public Result checkExecSVO  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false,  true, false,  true,  true, false}); }
    public Result checkExecTVO  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false, false,  true,  true,  true, false}); }
    public Result checkExecMTVO (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true,  true,  true,  true,  true, false}); }
    public Result checkExecPTVO (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true, false,  true,  true,  true, false}); }
    public Result checkExecSTVO (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false,  true,  true,  true,  true, false}); }
    public Result checkExecMF   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true,  true, false, false, false,  true}); }
    public Result checkExecPF   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true, false, false, false, false,  true}); }
    public Result checkExecSF   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false,  true, false, false, false,  true}); }
    public Result checkExecTF   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false, false,  true, false, false,  true}); }
    public Result checkExecVF   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false, false, false,  true, false,  true}); }
    public Result checkExecOF   (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false, false, false, false,  true,  true}); }
    public Result checkExecMTF  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true,  true,  true, false, false,  true}); }
    public Result checkExecPTF  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true, false,  true, false, false,  true}); }
    public Result checkExecSTF  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false,  true,  true, false, false,  true}); }
    public Result checkExecMVF  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true,  true, false,  true, false,  true}); }
    public Result checkExecPVF  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true, false, false,  true, false,  true}); }
    public Result checkExecSVF  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false,  true, false,  true, false,  true}); }
    public Result checkExecMOF  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true,  true, false, false,  true,  true}); }
    public Result checkExecPOF  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true, false, false, false,  true,  true}); }
    public Result checkExecSOF  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false,  true, false, false,  true,  true}); }
    public Result checkExecTVF  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false, false,  true,  true, false,  true}); }
    public Result checkExecTOF  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false, false,  true, false,  true,  true}); }
    public Result checkExecVOF  (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false, false, false,  true,  true,  true}); }
    public Result checkExecMTVF (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true,  true,  true,  true, false,  true}); }
    public Result checkExecPTVF (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true, false,  true,  true, false,  true}); }
    public Result checkExecSTVF (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false,  true,  true,  true, false,  true}); }
    public Result checkExecMTOF (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true,  true,  true, false,  true,  true}); }
    public Result checkExecPTOF (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true, false,  true, false,  true,  true}); }
    public Result checkExecSTOF (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false,  true,  true, false,  true,  true}); }
    public Result checkExecMVOF (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true,  true, false,  true,  true,  true}); }
    public Result checkExecPVOF (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true, false, false,  true,  true,  true}); }
    public Result checkExecSVOF (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false,  true, false,  true,  true,  true}); }
    public Result checkExecTVOF (String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false, false,  true,  true,  true,  true}); }
    public Result checkExecMTVOF(String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true,  true,  true,  true,  true,  true}); }
    public Result checkExecPTVOF(String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] { true, false,  true,  true,  true,  true}); }
    public Result checkExecSTVOF(String signature, ConfigData cd){ return checkExecMTVOF(signature, cd, new boolean[] {false,  true,  true,  true,  true,  true}); }

    // the F is especially important for setters to check the correctness of the set values
    // But in OOP we might/should check always the variables -> may be we supply further Annotations to control that??? 
    public Result checkExecMTVOF(String signature, ConfigData cd, boolean[] pstvof){ // m==p&s
    
        ContextInfo ctx = new ContextInfo(signature, (isConstructor(signature)?"Konstruktortest":"Methodentest")
                                              +"("+wordListPSxxx(pstvof, "Modifier", "Zugriffsmodifier", "static-Modifier", "Rückgabetyp", "Rückgabewert", "System.out-Ausgabe", "Variablenwerte")+")");
        cd = merge(cd);
        if (cd.dbg(1)) sopl("starting checkExecMTVOF("+signature+", "+cd+", "+ctx.typeOfTest+")");
        
        if (!hasElems())     // check if stuSol can be used
            return new Result(false, ctx, hasElems_errorText(ctx));

        // sopl("aaa "+this);
        Executable xs = stuAllE.get(signatureTrafoS(signature));
        if (xs==null)
            return new Result(false, ctx, executableNotFound_errorText(ctx));

        Executable xr = refChkE.get(signatureTrafoR(signature));
        if (xr==null)   // not to be checked -> existing type in ms is correct by default!!!!
            return new Result(true, ctx, "ok, keine zu testenden Vorgaben zu "+signature+" gefunden"); 
        
        cd.timeOutInMs = getTimeout(xr, cd.timeOutInMs);
        if (cd.dbg(3)) sopl("checkExecMTVOF("+signature+", "+cd+") uses xr:"+xr+" and xs:"+xs);
        
        Class[]    paT = xr.getParameterTypes(); // we trust our refSol (todo: make tests with alternative parameter types!)
        String    meTy = getMemberType(xs);
        boolean didChk = false;
        String   okStr = "";
        
        
        ////////////////////////////////////  handle modifier tests (public/protected/private and/or static) //////////////////////
        
        if (pstvof[0]||pstvof[1]){ // modifier test, see checkModifiers : todo separate m to p and/or s
            int mask    = ( pstvof[0] ? java.lang.reflect.Modifier.PUBLIC   
                                      + java.lang.reflect.Modifier.PRIVATE  
                                      + java.lang.reflect.Modifier.PROTECTED : 0)
                        + ( pstvof[1] ? java.lang.reflect.Modifier.STATIC    : 0);
                                      
            int xsModif = xs.getModifiers() & mask;
            int xrModif = xr.getModifiers() & mask;
            
            if (xsModif!=xrModif){  // have to check if there are other modifier settings allowed...
                Variants xv = getAnnotation(xr, Variants.class);
                if (xv == null) // there are no alternatives!
                    return new Result(false, ctx, wrongModifierType_errorText(signature, "Modifiertest", meTy, xrModif, xsModif)); 

                for (ModifierCheck mc : ModifierCheck.parse(xv.modifierChecks())){
                    // sopl("checking "+mc);
                    if (!mc.ok(xsModif))
                        return new Result(false, ctx, wrongModifierType_errorText(signature, "Modifiertest", meTy, mc, xsModif)); 
                }
            }
            okStr += (okStr.length()==0?"":"; ") + "Modifiertest bestanden ("+ModifierCheck.toStr(xsModif, " ")+" ist ok)"; 
        }

        ////////////////////////////////////  handle retType tests  //////////////////////

        if (pstvof[2]){    // return type comparizon (for constructor we use the declaring class)
            Class<?> xsRt = (xs instanceof Method) ? ((Method     )xs).getReturnType()
                                                   : ((Constructor)xs).getDeclaringClass();
            Class<?> xrRt = (xr instanceof Method) ? ((Method     )xr).getReturnType()
                                                   : ((Constructor)xr).getDeclaringClass();
                                                   
            if (!xsRt.equals(xrRt) && !otherTypeOk(xr, xsRt, xrRt)) // have to check if there are other return types allowed...
//             if (!xsRt.equals(xrRt)){ // have to check if there are other return types allowed...
// 
//                 boolean ok=false;
//                 Variants xv = xr.getAnnotation(Variants.class);
//                 if (xv != null){
//                     Class[] ot = xv.otherTypes();
//                     if (ot!=null)
//                         for (Class<?> ort : ot){
//                             if (xsRt.equals(ort)){
//                                 ok=true;
//                                 break;
//                             }
//                         }
//                 }
// 
//                 if (!ok)
                return new Result(false, ctx, wrongReturnType_errorText(signature, "Rückgabetyptest", xrRt, xr.getAnnotation(Variants.class), xsRt)); 
            //}

            okStr += (okStr.length()==0?"":"; ")+"Rückgabetyptest bestanden ("+xsRt+" ist ok)"; 
        }
        

        
        ////////////////////////////////////  handle retVal and Out tests //////////////////////
        if (pstvof[3] || pstvof[4] || pstvof[5]){
            // a) check predefined parameter lists (if some are defined in the *_refSolClass for this Check)
            if (true){
                ExecFiPaDat[] datV = getExecFiPaDatFor(xr, cd, null, 'v');
                for (ExecFiPaDat dat : datV)
                    for (String paStr : dat.paStrArr){
                        didChk=true;
                        Result res = checkExecMTVOF(signature, xr, xs, dat, paStr, cd, ctx, null, pstvof);
                        if (res!=null)  // ==null means no error found
                            return res; // found an error -> test failed -> can stop now
                        dat.curStrIdx++;
                    }
            }
            else {    
                for (String paStr : getTestParamsArrayFor(xr, cd)){
                    didChk=true;
                    Result res = checkExecMTVOF(signature, xr, xs, null, paStr, cd, ctx, null, pstvof);
                    if (res!=null)  // ==null means no error found
                        return res; // found an error -> test failed -> can stop now
                }
            }

            // b) check also predefined parameter list ranges (if some are defined in the *_refSolClass for this Check)
            for (String rangeStr : getTestRangesArrayFor(xr, cd)){
                Range[] ranges = Range.rangeArrFromStr(paT, rangeStr); // try to parse a set of range data for the parameters
                if (!Range.allAreValid(ranges) || ranges.length==0){
                    sopl("ERROR: Check.checkExecMTVOF found bad range definition in: "+rangeStr);
                    continue;
                }

                int testsPerRange = ranges[0].num;    // todo: think about a mode which uses a number per range and testing of all their combinations...
                if (cd.dbg(1)) sopl("testing "+signature+" with ranges from "+rangeStr+" parsed as "+Range.dbgStr(ranges, this));
                // sopl("ddddd "+Range.dbgStr(ranges));
                for (int i=0; i<testsPerRange; i++){
                    didChk=true;
                    String paStr = Range.toParamsStr(ranges, this); // generate a parameter string (one parameter for each value)
                    // sopl("ssssssssss "+paStr);
                    Result res = checkExecMTVOF(signature, xr, xs, null, paStr, cd, ctx, i+1, pstvof);
                    if (res!=null)
                        return res;
                }
            }

            // c) check predefined field values and optional parameter lists (if some are defined in the *_refSolClass for this Check)
            ExecFiPaDat[] datF = getExecFiPaDatFor(xr, cd, null, 'f');
            for (ExecFiPaDat dat : datF){
                for (int i=0; i<dat.paStrArr.length; i++){
                    didChk=true;
                    String paStr = dat.paStrArr[i];
                    Result res = checkExecMTVOF(signature, xr, xs, dat, paStr, cd, ctx, null, pstvof);
                    if (res!=null)  // ==null means no error found
                        return res; // found an error -> test failed -> can stop now
                    dat.curStrIdx++;
                }
            }
        }

        ////////////////////////////////////  handle field values tests  //////////////////////

//         if (pstvof[5]){ // 
//             Object objR = objIfNeeded(refSol, xr); // objR must be != null for non-static fields; for static ones it could be null too
//             Object objS = objIfNeeded(stuSol, xs); // objS must be != null for non-static fields; for static ones it could be null too
// 
//             fr.setAccessible(true);
//             fs.setAccessible(true);
//             Object valR = null; try { valR = fr.get(objR); } catch (Exception e){} // e.g. IllegalAccessException.
//             Object valS = null; try { valS = fs.get(objS); } catch (Exception e){}
// 
//             if (!isEqual(valR, valS, cd)){ // IMPORTANT: note that  Long(0) != Integer(0)
//                 if (cd.dbg(2)) sopl("R("+ (valR==null ? "nullType" : humanStr4JavaType(valR.getClass()))
//                                  +") S("+ (valS==null ? "nullType" : humanStr4JavaType(valS.getClass()))+"):"+asString(valR)+"  <=!=>  "+asString(valS)+" -> "+valR.equals(valS));
//                 return new Result(false, ctx, fieldValueDiffers_errorText(name, valR, valS, cd));
//             }        
//             okStr += (okStr.length()==0?"":"; ")+"Werttest bestanden ("+valS+" ist ok)"; 
//         }

        if (!didChk){
            if (paT.length!=0){ 
                if (cd.dbg(1)) 
                    sopl("Error: checkExecMTVOF("+signature+", "+ctx.typeOfTest+") called with no (valid) test parameter / ranges definitions! No test was performed!");
            }
            else { // has no parameters -> no parameter / range definitions needed!
                Result res = checkExecMTVOF(signature, xr, xs, null, "", cd, ctx, null, pstvof);
                if (res!=null)  // ==null means no error found
                    return res; // found an error -> test failed -> can stop now
            }
        }
        
        return new Result(true, ctx, "ok, "+meTy+" "+signature+" hat den "+ctx.typeOfTest+" bestanden"+(okStr.length()==0?"":(" : "+okStr)) ); 
    }
    
    Result checkExecMTVOF(String signature, Executable xr, Executable xs, ExecFiPaDat dat, String paStr, ConfigData cd, ContextInfo ctx, Integer dbg_i, boolean[] pstvof){
        Object objR  = objIfNeeded(refSol, xr); // objR must be != null for non-static methods/constr; for static ones it could be null too
        Object objS  = objIfNeeded(stuSol, xs); // objS must be != null for non-static methods/constr; for static ones it could be null too
        String okStr = "";

        String fiValHint = null;
        if (dat!=null && dat.fiNamArr.length>0){ // set the values in both objects
            Object[] fiValArr = objArrFromParamStr(dat.fiTypArr, dat.fiStrArr[dat.curStrIdx]); // the array with the parsed parameters
            fiValHint = "";
            for (int i=0; i<dat.fiNamArr.length; i++){
                String fiNam = dat.fiNamArr[i];
                Field fs = stuAllF.get(fiNam);
                if (fs==null)
                    return new Result(false, ctx, fieldNotFound_errorText(ctx, fiNam));
                Field fr = refChkF.get(fiNam);
                if (fr==null)   // not to be checked -> existing return type in ms is correct by default!!!!
                    return new Result(true, ctx, "ok, keine zu testenden Vorgaben zur Variablen "+fiNam+" gefunden"); 
                
                fiValHint+=(fiValHint.length()==0?"":"; ")+fiNam+"="+fiValArr[i];
                
                fr.setAccessible(true);  // before setting/initialisation we ensure that it is accessinle
                fs.setAccessible(true);
                try {fr.set(objR, fiValArr[i]); } catch (Exception e){} // e.g. IllegalAccessException (should not happen)
                try {fs.set(objS, fiValArr[i]); } catch (Exception e){}
            }  // setting/initialization of the fields is ready; now we have a "gound zero" for comparizons after the execution... 
        }

        // sopl("testing "+signature+" with "+paStr+" parsed as ...");
        Object[] paArrOri = objArrFromParamStr(xr.getParameterTypes(), paStr); // the array with the parsed parameters
        if (cd.dbg(1)) sopl("testing "+signature + (dbg_i==null ? "" : (dbg_i+". : ")) + (paStr.equals("") 
                                     ? "" 
                                     : (" with "+paStr+" parsed as "+objParamList(paArrOri)
                                                +" typesParamList:"+typeParamList(objs2types(paArrOri)     , true)
                                                +" typesMrParam  :"+typeParamList(xr.getParameterTypes(), true) )));
        // 
        // Separate the original parameter array from the parameter arrays for ref & stu because those may be changed during execution!
        Object[] paArrRef = (Object[])deepCopy(paArrOri); // we should not use objArrFromParamStr, because random values could be included...
        Object[] paArrStu = (Object[])deepCopy(paArrOri); // we should not use objArrFromParamStr, because random values could be included...
        
        xr.setAccessible(true);
        xs.setAccessible(true);
        
        Object retR=null; try { retR = timeOutChecked_invoke(xr, objR, paArrRef, cd, ctx); } catch (Exception e){    // was: mr.invoke(objR, paArrRef);
            sopl("refSolExec "+dbg_catchInfoInvoke(signature, paStr, objR, paArrOri, e)); 
            return null;                                                                            //internal error -> per default ok for the stud!!! 
        }
        String refSysOut = ctx.lastOutput;
        Object retS=null; try { retS = timeOutChecked_invoke(xs, objS, paArrStu, cd, ctx); } catch (Exception e){ 
            // sopl("stuSolExec "+dbg_catchInfoInvoke(signature, paStr, objS, paArrOri, e)+"  yyyyyyy:"+e.getCause()+"\n"+Arrays.toString(e.getCause().getStackTrace())); 
            return new Result(false, ctx, exceptionDuringExecution_errorText(signature, paArrOri, e, cd));
        }

        if (cd.dbg(3)) sopl("got values stu_ret >>>"+retS+"<<< stu_out:>>>"+ctx.lastOutput+"<<<\n"
                           +"           ref_ret >>>"+retR+"<<< ref_out:>>>"+     refSysOut+"<<<");
        
        
        if (cd.specCompareFunc!=null)
            return cd.specCompareFunc.specialCompare(signature, xr, xs, paStr, cd, ctx, dbg_i, pstvof, this, objR, objS, paArrOri, retR, refSysOut, retS, ctx.lastOutput);


        // default comparizons of the results
        
        if (pstvof[3]){ // compare return values 
            if (!isEqual(retR, retS, cd)){ // IMPORTANT: note that  Long(0) != Integer(0)
                if (cd.dbg(2)) sopl("R("+ (retR==null ? "nullType" : humanStr4JavaType(retR.getClass()))
                                 +") S("+ (retS==null ? "nullType" : humanStr4JavaType(retS.getClass()))+"):"+asString(retR)+"  <=!=>  "+asString(retS)+" -> "+retR.equals(retS));
                return new Result(false, ctx, returnValueDiffers_errorText(signature, fiValHint, paArrOri, retR, retS, cd));
            }
        }
        
        if (pstvof[4]){ // compare sysOut 
            if (!isEqual(refSysOut, ctx.lastOutput, cd)){ 
                if (cd.dbg(2)) sopl("sysOut differs: "+refSysOut+"  <=!=>  "+ctx.lastOutput);
                return new Result(false, ctx, systemOutDiffers_errorText(signature, fiValHint, paArrOri, refSysOut, ctx.lastOutput, cd));
            }
        }

        if (pstvof[5] && dat!=null && dat.fiNamArr.length>0){ // have to compare all the fields we filled above, but only if fields are defined
            for (int i=0; i<dat.fiNamArr.length; i++){
                String fiNam = dat.fiNamArr[i];
                Field fs = stuAllF.get(fiNam);
                if (fs==null)
                    return new Result(false, ctx, fieldNotFound_errorText(ctx, fiNam));
                Field fr = refChkF.get(fiNam);
                if (fr==null)   // not to be checked -> existing return type in ms is correct by default!!!!
                    return new Result(true, ctx, "ok, keine zu testenden Vorgaben zur Variablen "+fiNam+" gefunden"); 
                
                fr.setAccessible(true);  // before setting/initialisation we ensure that it is accessinle
                fs.setAccessible(true);
                Object valR = null; try { valR = fr.get(objR); } catch (Exception e){} // e.g. IllegalAccessException (should not happen)
                Object valS = null; try { valS = fs.get(objS); } catch (Exception e){}

                if (!isEqual(valR, valS, cd)){ // IMPORTANT: note that  Long(0) != Integer(0)
                    if (cd.dbg(2)) sopl("R("+ (valR==null ? "nullType" : humanStr4JavaType(valR.getClass()))
                                     +") S("+ (valS==null ? "nullType" : humanStr4JavaType(valS.getClass()))+"):"+asString(valR)+"  <=!=>  "+asString(valS)+" -> "+valR.equals(valS));
                    return new Result(false, ctx, fieldValueDiffers_errorText(fiNam, signature, paArrOri, valR, valS, cd));
                }
            }  // field checks are ready

        }
        
        if (true){ // todo: make this controlable from outside for each parameter or even for any further objects...
            if (!isEqual(paArrRef, paArrStu, cd))
            	return new Result(false, ctx, badParamValues_errorText(signature, paArrOri, paArrRef, paArrStu, cd));
        }

        // sopl ("AAAA");
        return null;  // shows the calling checkExecMTVOF-Method, that the current call was ok
    }

    public Result checkReturnValue(String signature){ 
        return checkResult(signature, null, true);
    }
    
    public Result checkReturnValue(String signature, ConfigData cd){ 
        return checkResult(signature, cd, true);
    }
    
    public Result checkSystemOut(String signature){ 
        return checkResult(signature, null, false);
    }
    
    public Result checkSystemOut(String signature, ConfigData cd){ 
        return checkResult(signature, cd, false);
    }
    
    public Result checkResult(String signature, ConfigData cd, boolean doChkRetVal){ // !doChkRetVal -> check sysOut
        // TODO: may be the method is for "normal case" only!!! -> more implementation may be needed when e.g. object variables are involved????
        //        => currently ok for static or non-static methods which do not use other member/class variables as input 
        //           i.e. it is ok for those where the invoke-this can be null or an object constructed by the default constructor...
        
        ContextInfo ctx = new ContextInfo(signature, doChkRetVal ? "Funktionstest:Rückgabewert" : "Funktionstest:System.out-Ausgabe");
        cd = merge(cd); String what = (doChkRetVal?"chkRet":"chkOut");
        if (cd.dbg(1)) sopl("starting checkResult("+signature+", "+cd+", "+what+")");
        
        if (!hasElems())     // check if stuSol can be used
            return new Result(false, ctx, hasElems_errorText(ctx));
        
        // sopl("aaa "+this);
        Executable xs = stuAllE.get(signatureTrafoS(signature));
        if (xs==null)
            return new Result(false, ctx, executableNotFound_errorText(ctx));

        Executable xr = refChkE.get(signatureTrafoR(signature));
        if (xr==null)   // not to be checked -> existing type in ms is correct by default!!!!
            return new Result(true, ctx, "ok, keine zu testenden Vorgaben zu "+signature+" gefunden"); 
        
        cd.timeOutInMs = getTimeout(xr, cd.timeOutInMs);
        if (cd.dbg(3)) sopl("checkResult("+signature+", "+cd+") uses xr:"+xr+" and xs:"+xs);
        Class[] paT = xr.getParameterTypes(); // we trust our refSol (todo: make tests with alternative parameter types!)
        boolean didChk=false;
        
        // a) check predefined parameter lists (if some are defined in the *_refSolClass for this Check)
        if (true){
            ExecFiPaDat[] datV = getExecFiPaDatFor(xr, cd, null, 'v');
            for (ExecFiPaDat dat : datV)
                for (String paStr : dat.paStrArr){
                    didChk=true;
                    Result res = checkResult(signature, xr, xs, dat, paStr, cd, ctx, null, doChkRetVal);
                    if (res!=null)  // ==null means no error found
                        return res; // found an error -> test failed -> can stop now
                    dat.curStrIdx++;
                }
        }
        else {    
            for (String paStr : getTestParamsArrayFor(xr, cd)){
                didChk=true;
                Result res = checkResult(signature, xr, xs, null, paStr, cd, ctx, null, doChkRetVal);
                if (res!=null)  // ==null means no error found
                    return res; // found an error -> test failed -> can stop now
            }
        }

        // b) check also predefined parameter list ranges (if some are defined in the *_refSolClass for this Check)
        for (String rangeStr : getTestRangesArrayFor(xr, cd)){
            Range[] ranges = Range.rangeArrFromStr(paT, rangeStr); // try to parse a set of range data for the parameters
            if (!Range.allAreValid(ranges) || ranges.length==0){
                sopl("ERROR: Check.checkResult found bad range definition in: "+rangeStr);
                continue;
            }
    
            int testsPerRange = ranges[0].num;    // todo: think about a mode which uses a number per range and testing of all their combinations...
            if (cd.dbg(1)) sopl("testing "+signature+" with ranges from "+rangeStr+" parsed as "+Range.dbgStr(ranges, this));
            // sopl("ddddd "+Range.dbgStr(ranges));
            for (int i=0; i<testsPerRange; i++){
                didChk=true;
                String paStr = Range.toParamsStr(ranges, this); // generate a parameter string (one parameter for each value)
                // sopl("ssssssssss "+paStr);
                Result res = checkResult(signature, xr, xs, null, paStr, cd, ctx, i+1, doChkRetVal);
                if (res!=null)
                    return res;
            }
        }

        // c) check predefined field values and optional parameter lists (if some are defined in the *_refSolClass for this Check)
        ExecFiPaDat[] datF = getExecFiPaDatFor(xr, cd, null, 'f');
        for (ExecFiPaDat dat : datF){
            for (int i=0; i<dat.paStrArr.length; i++){
                didChk=true;
                String paStr = dat.paStrArr[i];
                Result res = checkResult(signature, xr, xs, dat, paStr, cd, ctx, null, doChkRetVal);
                if (res!=null)  // ==null means no error found
                    return res; // found an error -> test failed -> can stop now
                dat.curStrIdx++;
            }
        }

        if (!didChk){
            if (paT.length!=0){ 
                if (cd.dbg(1)) 
                    sopl("Error: checkResult("+signature+", "+what+") called with no (valid) test parameter / ranges definitions! No test was performed!");
            }
            else { // has no parameters -> no parameter / range definitions needed!
                Result res = checkResult(signature, xr, xs, null, "", cd, ctx, null, doChkRetVal);
                if (res!=null)  // ==null means no error found
                    return res; // found an error -> test failed -> can stop now
            }
        }
        
        return new Result(true, ctx, "ok, "+signature+" hat diesen Funktionstest bestanden"); 
    }

    // TreeMap<Long,String>  outputLogs = new TreeMap<Long,String>();
    ByteArrayOutputStream outputLog_stream;
    PrintStream           outputLog_origSystemOut;
    // long                  outputLog_currKey;
    void begOutLog(ContextInfo ctx){
        // outputLog_currKey       = (long)(Math.random()*Long.MAX_VALUE);
        outputLog_stream        = new ByteArrayOutputStream();
        outputLog_origSystemOut = System.out;
        ctx.lastOutput=null;

        // sopl("beg_"+outputLog_currKey+" : stopping output to orig out now");    // mark redirection in orig
        System.setOut(new PrintStream(outputLog_stream));    // set the new stream
        // sopl("beg_"+outputLog_currKey);     // this should be the first line in our temporary output stream
        // return outputLog_currKey;
    }

    void endOutLog(ContextInfo ctx){
        // sopl("end_"+outputLog_currKey);     // this should be the first line in our temporary output stream
        System.out.flush();                 // to be sure; may be use of PrintWriter (vs. PrintStream) would eliminate the need of such a flush
        System.setOut(outputLog_origSystemOut);
        String o=outputLog_stream.toString();
        ctx.lastOutput = (o.length()>0 && o.charAt(o.length()-1)=='\n') ? o.substring(0, o.length()-1) : o;
        // outputLogs.put(currOutLogKey, ctx.lastOutput);
        // return outputLog_currKey;
    }
    
    // NOTE: this method is only used internally (-> non-public!)
    Object timeOutChecked_invoke(Executable x, Object o, Object[] pa, ConfigData cd, ContextInfo ctx) 
            throws IllegalAccessException, InvocationTargetException, InstantiationException {
        // We have 3 use cases:
        //   a) we call a method/constructor in the reference solution -> here we assume that there is no timeout handling needed => may be we have to use ConfigData to control this... (todo? later?)
        //   b) in an JUnit controlled environment in <className>_check.java -> here JUnit triggers timeouts, defined e.g. via @Test(timeout = 400)
        //   c) in a <className>_refSolClass.java controlled environment     -> here JUnit is not involved -> timeouts must be trigged in Check.timeOutChecked_invoke
        try {
            Object ret;
            if (RefSolClass.class.isAssignableFrom(x.getDeclaringClass())){ // case a)
                if (cd.dbg(3))
                    sopl("timeOutChecked_invoke("+ctx.signature+") assumes call of reference class method and does NOT make timeout handling!");
                begOutLog(ctx);
                return (x instanceof Method) ? ((Method     )x).invoke  (o, pa)  // assuming that our reference code has no infinite loops...
                                             : ((Constructor)x).newInstance(pa); // 
            }
            if (Arrays.toString(new Exception().getStackTrace()).indexOf("at org.junit.")>=0){  // case b)
                if (cd.dbg(2))
                    sopl("timeOutChecked_invoke("+ctx.signature+") assumes JUnit environment and does NOT make timeout handling!");
                begOutLog(ctx);
                return(x instanceof Method) ? ((Method     )x).invoke  (o, pa)  // assuming JUnit observes the invokation
                                            : ((Constructor)x).newInstance(pa); //
            }

            int toMs = cd.timeOutInMs;  // case c)
            if (cd.dbg(2)) 
                sopl("timeOutChecked_invoke("+ctx.signature+") assumes NON-JUnit environment and starts timeout handling now.");
            begOutLog(ctx);
            return new MethodCallWrapper(x, o, pa, true, toMs, this).doExecMethod(); 
        }
        finally {   // to ensure that even in case of an exception / throwable the output will be set to the "normal" output stream!
            endOutLog(ctx); 
        }        
    }

    @FunctionalInterface // to allow implementation of special comparisons, e.g. for random return values or random outputs 
    public interface Function_specialCompareOfResult {
        Result specialCompare(String signature, Executable xr, Executable xs, String paStr, ConfigData cd, ContextInfo ctx, Integer dbg_i, boolean[] pstvof,
                              Check chk, Object objR, Object objS, Object[] paArr, Object retR, String refSysOut, Object retS, String stuSysOut);  // returns null if ok
    }
    
    public static String normalizeString(String s){
        return (s==null) ? s : s.replaceAll(" +", " ").trim();
    }
    
//     public static Result normalizedStringCompare_ret_checkFunc
//                              (String signature, Method mr, Method ms, String paStr, Check.ConfigData cd, Check.ContextInfo ctx, Integer dbg_i, boolean doChkRetVal,
//                               Check thisChk, Object objR, Object objS, Object[] paArr, Object retR, String refSysOut, Object retS, String stuSysOut){
//         Boolean ok = null; 
//         try { 
//             String nrmRetS = normalizeString(retS); Inkompatible Typen: Object kann nicht in String konvertiert werden
//             String nrmRetR = normalizeString(retR); Inkompatible Typen: Object kann nicht in String konvertiert werden
//             ok = ((nrmRetS==null) == (nrmRetR==null)) && (nrmRetS==null || nrmRetS.equals(nrmRetR));
//         } catch (Exception e){}
//         
//         if (ok==null || !ok)
//             return thisChk.new Result(false, ctx, thisChk.returnValueDiffers_errorText(signature, paArr, retR, retS, cd));
//         return null;
//     }
//     
//     public static Result normalizedStringCompare_out_checkFunc
//                              (String signature, Method mr, Method ms, String paStr, Check.ConfigData cd, Check.ContextInfo ctx, Integer dbg_i, boolean doChkRetVal,
//                               Check thisChk, Object objR, Object objS, Object[] paArr, Object retR, String refSysOut, Object retS, String stuSysOut){
//         Boolean ok = null; 
//         try { 
//             String nrmRetS = normalizeString(retS); Inkompatible Typen: Object kann nicht in String konvertiert werden
//             String nrmRetR = normalizeString(retR); Inkompatible Typen: Object kann nicht in String konvertiert werden
//             ok = ((nrmRetS==null) == (nrmRetR==null)) && (nrmRetS==null || nrmRetS.equals(nrmRetR));
//         } catch (Exception e){}
//         
//         if (ok==null || !ok)
//             return thisChk.new Result(false, ctx, thisChk.systemOutDiffers_errorText(signature, paArr, refSysOut, ctx.lastOutput, cd));
//         return null;
//     }
//     
    // NOTE: this method is only used internally (-> non-public!)
    Result checkResult(String signature, Executable xr, Executable xs, ExecFiPaDat dat, String paStr, ConfigData cd, ContextInfo ctx, Integer dbg_i, boolean doChkRetVal){
        Object objR = objIfNeeded(refSol, xr); // objR must be != null for non-static methods/constr; for static ones it could be null too
        Object objS = objIfNeeded(stuSol, xs); // objS must be != null for non-static methods/constr; for static ones it could be null too

        String fiValHint = null;
        if (dat!=null && dat.fiNamArr.length>0){ // set the values in both objects
            Object[] fiValArr = objArrFromParamStr(dat.fiTypArr, dat.fiStrArr[dat.curStrIdx]); // the array with the parsed parameters
            fiValHint = "";
            for (int i=0; i<dat.fiNamArr.length; i++){
                String fiNam = dat.fiNamArr[i];
                Field fs = stuAllF.get(fiNam);
                if (fs==null)
                    return new Result(false, ctx, fieldNotFound_errorText(ctx, fiNam));
                Field fr = refChkF.get(fiNam);
                if (fr==null)   // not to be checked -> existing return type in ms is correct by default!!!!
                    return new Result(true, ctx, "ok, keine zu testenden Vorgaben zur Variablen "+fiNam+" gefunden"); 
                
                fiValHint+=(fiValHint.length()==0?"":"; ")+fiNam+"="+fiValArr[i];
                
                fr.setAccessible(true);  // before setting/initialisation we ensure that it is accessinle
                fs.setAccessible(true);
                try {fr.set(objR, fiValArr[i]); } catch (Exception e){} // e.g. IllegalAccessException (should not happen)
                try {fs.set(objS, fiValArr[i]); } catch (Exception e){}
            }  // setting/initialization of the fields is ready; now we have a "gound zero" for comparizons after the execution... 
        }

        // sopl("testing "+signature+" with "+paStr+" parsed as ...");
        Object[] paArr = objArrFromParamStr(xr.getParameterTypes(), paStr); // the array with the parsed parameters
        if (cd.dbg(1)) sopl("testing "+signature + (dbg_i==null ? "" : (dbg_i+". : ")) + (paStr.equals("") 
                                     ? "" 
                                     : (" with "+paStr+" parsed as "+objParamList(paArr)
                                                +" typesParamList:"+typeParamList(objs2types(paArr)     , true)
                                                +" typesMrParam  :"+typeParamList(xr.getParameterTypes(), true) )));

        xr.setAccessible(true);
        xs.setAccessible(true);
        
        Object retR=null; try { retR = timeOutChecked_invoke(xr, objR, paArr, cd, ctx); } catch (Exception e){    // was: mr.invoke(objR, paArr);
            sopl("refSolExec "+dbg_catchInfoInvoke(signature, paStr, objR, paArr, e)); 
            return null;                                                                            //internal error -> per default ok for the stud!!! 
        }
        String refSysOut = ctx.lastOutput;
        Object retS=null; try { retS = timeOutChecked_invoke(xs, objS, paArr, cd, ctx); } catch (Exception e){ 
            // sopl("stuSolExec "+dbg_catchInfoInvoke(signature, paStr, objS, paArr, e)+"  yyyyyyy:"+e.getCause()+"\n"+Arrays.toString(e.getCause().getStackTrace())); 
            return new Result(false, ctx, exceptionDuringExecution_errorText(signature, paArr, e, cd));
        }

        if (cd.dbg(3)) sopl("got values stu_ret >>>"+retS+"<<< stu_out:>>>"+ctx.lastOutput+"<<<\n"
                           +"           ref_ret >>>"+retR+"<<< ref_out:>>>"+     refSysOut+"<<<");
        if (cd.specCompareFunc!=null)                                                                   //     P      S     retT     retV         Out        Fields      
            return cd.specCompareFunc.specialCompare(signature, xr, xs, paStr, cd, ctx, dbg_i, new boolean[]{false, false, false, doChkRetVal, !doChkRetVal, false}, this, objR, objS, paArr, retR, refSysOut, retS, ctx.lastOutput);

        if (doChkRetVal){
            if (!isEqual(retR, retS, cd)){ // IMPORTANT: note that  Long(0) != Integer(0)
                if (cd.dbg(2)) sopl("R("+ (retR==null ? "nullType" : humanStr4JavaType(retR.getClass()))
                                 +") S("+ (retS==null ? "nullType" : humanStr4JavaType(retS.getClass()))+"):"+asString(retR)+"  <=!=>  "+asString(retS)+" -> "+retR.equals(retS));
                return new Result(false, ctx, returnValueDiffers_errorText(signature, fiValHint, paArr, retR, retS, cd));
            }
        }
        else { // have to perform a sysOut check
            if (!isEqual(refSysOut, ctx.lastOutput, cd)){ 
                if (cd.dbg(2)) sopl("sysOut differs: "+refSysOut+"  <=!=>  "+ctx.lastOutput);
                return new Result(false, ctx, systemOutDiffers_errorText(signature, fiValHint, paArr, refSysOut, ctx.lastOutput, cd));
            }
        }
        
        // sopl ("AAAA");
        return null;  // shows the calling checkResult-Method, that the current call was ok
    }


    // ................................... Return type  & Field type ...................................................


    public Result checkResult(String signature){
        ContextInfo ctx = new ContextInfo(signature, "Rückgabetyptest");
        if (!hasElems())     // check if stuSol can be used
            return new Result(false, ctx, hasElems_errorText(ctx));
        
        Executable xs = stuAllE.get(signatureTrafoS(signature));
        if (xs==null)
            return new Result(false, ctx, executableNotFound_errorText(ctx));
            
        Executable xr = refChkE.get(signatureTrafoR(signature));
        if (xr==null)   // not to be checked -> existing return type in ms is correct by default!!!!
            return new Result(true, ctx, "ok, keine zu testenden Vorgaben zu "+signature+" gefunden"); 
        
        Class<?> xsRt = (xs instanceof Method) ? ((Method     )xs).getReturnType()
                                               : ((Constructor)xs).getDeclaringClass();
        Class<?> xrRt = (xr instanceof Method) ? ((Method     )xr).getReturnType()
                                               : ((Constructor)xr).getDeclaringClass();
        if (!xsRt.equals(xrRt)){ // have to check if there are other return types allowed...

            boolean ok=false;
            Variants xv = xr.getAnnotation(Variants.class);
            if (xv != null){
                Class[] ot = xv.otherTypes();
                if (ot!=null)
                    for (Class<?> ort : ot){
                        if (xsRt.equals(ort)){
                            ok=true;
                            break;
                        }
                    }
            }
            
//             if (false){ 
//                 Annotation[]   aa = mr.getAnnotations();
//                 System.out.println( "mv "+mv+"  aa:"+aa );
//                 if (aa!=null){
//                     System.out.println("Annotations found : "+aa.length);
//                     for (int i=0; i<aa.length; i++)
//                         System.out.println("A"+i+" : "+aa[i]);
//                 }
//             }
            
            if (!ok)
                return new Result(false, ctx, wrongReturnType_errorText(signature, "Rückgabetyptest", xrRt, xv, xsRt)); 
        }
        
        return new Result(true, ctx, "ok, die Methode "+signature+" hat den Rückgabetyptest bestanden"); 
    }

    
    public Result checkFieldType(String name){
        ContextInfo ctx = new ContextInfo(name, "Variablentyptest");
        if (!hasElems())     // check if stuSol can be used
            return new Result(false, ctx, hasElems_errorText(ctx));
        
        Field fs = stuAllF.get(name);
        if (fs==null)
            return new Result(false, ctx, fieldNotFound_errorText(ctx));
            
        Field fr = refChkF.get(name);
        if (fr==null)   // not to be checked -> existing return type in ms is correct by default!!!!
            return new Result(true, ctx, "ok, keine zu testenden Vorgaben zur Variablen "+name+" gefunden"); 
        
        Class<?> sTy = fs.getType();
        Class<?> rTy = fr.getType();
        if (!rTy.equals(sTy) && !otherTypeOk(fr, sTy, rTy)) // have to check if there are other return types allowed...
            return new Result(false, ctx, wrongVariableType_errorText(name, "Variablentyptest", rTy, fr.getAnnotation(Variants.class), sTy)); 
        
        return new Result(true, ctx, "ok, die Variable "+name+" hat den Variablentyptest bestanden"); 
    }

    public static String wordListPSxxx(boolean[] pstv, String... s){
        boolean modi = pstv[0]&&pstv[1];
        String   ret = modi ? s[0] : "";
        int        i = modi ? 2    : 0;
        for (; i<pstv.length; i++)
        	if (pstv[i])
        		ret += (ret.length()==0?"":", ")+s[i+1];
    	return ret;
    }

    public boolean refSolStuSolEquality( Class<?> tyA,  Class<?> tyB){
        return  tyA.equals(refSol) && tyB.equals(stuSol)
             || tyA.equals(stuSol) && tyB.equals(refSol);
    }

    public boolean otherTypeOk(AccessibleObject a, Class<?> tyB, Class<?> tyA){
        if ( refSolStuSolEquality(tyA, tyB))
            return true;
            
        Variants av = a.getAnnotation(Variants.class);
        if (av == null)
            return false;
        
        Class[] ota = av.otherTypes();
        if (ota==null)
            return false;
        
        for (Class<?> ot : ota)
            if (tyB.equals(ot))
                return true;
        return false;
    }

    public Result checkFieldM  (String name){ return checkFieldMTV(name, null, new boolean[] { true,  true, false, false}); }
    public Result checkFieldP  (String name){ return checkFieldMTV(name, null, new boolean[] { true, false, false, false}); }
    public Result checkFieldS  (String name){ return checkFieldMTV(name, null, new boolean[] {false,  true, false, false}); }
    public Result checkFieldT  (String name){ return checkFieldMTV(name, null, new boolean[] {false, false,  true, false}); }
    public Result checkFieldV  (String name){ return checkFieldMTV(name, null, new boolean[] {false, false, false,  true}); }
    public Result checkFieldMT (String name){ return checkFieldMTV(name, null, new boolean[] { true,  true,  true, false}); }
    public Result checkFieldPT (String name){ return checkFieldMTV(name, null, new boolean[] { true, false,  true, false}); }
    public Result checkFieldST (String name){ return checkFieldMTV(name, null, new boolean[] {false,  true,  true, false}); }
    public Result checkFieldMV (String name){ return checkFieldMTV(name, null, new boolean[] { true,  true, false,  true}); }
    public Result checkFieldPV (String name){ return checkFieldMTV(name, null, new boolean[] { true, false, false,  true}); }
    public Result checkFieldSV (String name){ return checkFieldMTV(name, null, new boolean[] {false,  true, false,  true}); }
    public Result checkFieldTV (String name){ return checkFieldMTV(name, null, new boolean[] {false, false,  true,  true}); }
    public Result checkFieldMTV(String name){ return checkFieldMTV(name, null, new boolean[] { true,  true,  true,  true}); }
    public Result checkFieldPTV(String name){ return checkFieldMTV(name, null, new boolean[] { true, false,  true,  true}); }
    public Result checkFieldSTV(String name){ return checkFieldMTV(name, null, new boolean[] {false,  true,  true,  true}); }

    public Result checkFieldM  (String name, ConfigData cd){ return checkFieldMTV(name, cd, new boolean[] { true,  true, false, false}); }
    public Result checkFieldP  (String name, ConfigData cd){ return checkFieldMTV(name, cd, new boolean[] { true, false, false, false}); }
    public Result checkFieldS  (String name, ConfigData cd){ return checkFieldMTV(name, cd, new boolean[] {false,  true, false, false}); }
    public Result checkFieldT  (String name, ConfigData cd){ return checkFieldMTV(name, cd, new boolean[] {false, false,  true, false}); }
    public Result checkFieldV  (String name, ConfigData cd){ return checkFieldMTV(name, cd, new boolean[] {false, false, false,  true}); }
    public Result checkFieldMT (String name, ConfigData cd){ return checkFieldMTV(name, cd, new boolean[] { true,  true,  true, false}); }
    public Result checkFieldMV (String name, ConfigData cd){ return checkFieldMTV(name, cd, new boolean[] { true,  true, false,  true}); }
    public Result checkFieldTV (String name, ConfigData cd){ return checkFieldMTV(name, cd, new boolean[] {false, false,  true,  true}); }
    public Result checkFieldMTV(String name, ConfigData cd){ return checkFieldMTV(name, cd, new boolean[] { true,  true,  true,  true}); }

//    public Result checkFieldMTV(String name,                boolean cM, boolean cT, boolean cV){ return checkFieldMTV(name, null, new boolean[] {cM, cT, cV}); }
//    public Result checkFieldMTV(String name, ConfigData cd, boolean cM, boolean cT, boolean cV){ return checkFieldMTV(name,   cd, new boolean[] {cM, cT, cV}); }

    public Result checkFieldMTV(String name, ConfigData cd, boolean[] pstv){
        ContextInfo ctx = new ContextInfo(name, "Variablentest("+wordListPSxxx(pstv, "Modifier", "Zugriffsmodifier", "static-Modifier", "Typ", "Wert")+")");
        cd = merge(cd);
        if (cd.dbg(1)) sopl("starting checkFieldMTV("+name+", "+cd+", "+ctx.typeOfTest+")");
        
        if (!hasElems())     // check if stuSol can be used
            return new Result(false, ctx, hasElems_errorText(ctx));
        
        Field fs = stuAllF.get(name);
        if (fs==null)
            return new Result(false, ctx, fieldNotFound_errorText(ctx));
            
        Field fr = refChkF.get(name);
        if (fr==null)   // not to be checked -> existing return type in ms is correct by default!!!!
            return new Result(true, ctx, "ok, keine zu testenden Vorgaben zur Variablen "+name+" gefunden"); 
        
        String okStr="";
        
        if (pstv[0]||pstv[1]){ // modifier test, see checkModifiers : todo separate m to p and/or s
            String meTy = getMemberType(fs);
            int mask    = ( pstv[0] ? java.lang.reflect.Modifier.PUBLIC   
                                    + java.lang.reflect.Modifier.PRIVATE  
                                    + java.lang.reflect.Modifier.PROTECTED : 0)
                        + ( pstv[1] ? java.lang.reflect.Modifier.STATIC    : 0);
                                      
            int fsModif = fs.getModifiers() & mask;
            int frModif = fr.getModifiers() & mask;
            
            if (fsModif!=frModif){  // have to check if there are other modifier settings allowed...
                Variants fv = getAnnotation(fr, Variants.class);
                if (fv == null) // there are no alternatives!
                    return new Result(false, ctx, wrongModifierType_errorText(name, "Modifiertest", meTy, frModif, fsModif)); 

                for (ModifierCheck mc : ModifierCheck.parse(fv.modifierChecks())){
                    // sopl("checking "+mc);
                    if (!mc.ok(fsModif))
                        return new Result(false, ctx, wrongModifierType_errorText(name, "Modifiertest", meTy, mc, fsModif)); 
                }
            }
            okStr += "Modifiertest bestanden ("+ModifierCheck.toStr(fsModif, " ")+" ist ok)"; 
        }

        if (pstv[2]){    // type test, see checkFieldType
            Class<?> fsTy = fs.getType();
            Class<?> frTy = fr.getType();
            if (!frTy.equals(fsTy) && !otherTypeOk(fr, fsTy, frTy)) // have to check if there are other return types allowed...
                return new Result(false, ctx, wrongVariableType_errorText(name, "Variablentyptest", frTy, fr.getAnnotation(Variants.class), fsTy)); 
            okStr += (okStr.length()==0?"":"; ")+"Typtest bestanden ("+fsTy+" ist ok)"; 
        }
        
        if (pstv[3]){ // value test, see checkFieldValue
            Object objR = objIfNeeded(refSol, fr); // objR must be != null for non-static fields; for static ones it could be null too
            Object objS = objIfNeeded(stuSol, fs); // objS must be != null for non-static fields; for static ones it could be null too

            fr.setAccessible(true);
            fs.setAccessible(true);
            Object valR = null; try { valR = fr.get(objR); } catch (Exception e){} // e.g. IllegalAccessException.
            Object valS = null; try { valS = fs.get(objS); } catch (Exception e){}

            if (!isEqual(valR, valS, cd)){ // IMPORTANT: note that  Long(0) != Integer(0)
                if (cd.dbg(2)) sopl("R("+ (valR==null ? "nullType" : humanStr4JavaType(valR.getClass()))
                                 +") S("+ (valS==null ? "nullType" : humanStr4JavaType(valS.getClass()))+"):"+asString(valR)+"  <=!=>  "+asString(valS)+" -> "+valR.equals(valS));
                return new Result(false, ctx, fieldValueDiffers_errorText(name, valR, valS, cd));
            }        
            okStr += (okStr.length()==0?"":"; ")+"Werttest bestanden ("+valS+" ist ok)"; 
        }

        return new Result(true, ctx, "ok, die Variable "+name+" hat den "+ctx.typeOfTest+" bestanden"+(okStr.length()==0?"":(" : "+okStr)) ); 
        
    }
    
//     public Result checkFieldMTV(String name){
//         Result rM = checkFieldType(name);
//         Result rT = checkFieldType(name);
//         Result rV = checkFieldType(name);
//         return new Result(rM.ok() && rT.ok() && rV.ok(), new ContextInfo(name, "kombinierter Test"), rM.info+" UND "+rM.info+" UND "+rM.info); 
//     }
    
    // ................................... Modifier ...................................................

    // https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/AnnotatedElement.html#getAnnotation-java.lang.Class-
    public <A extends Annotation> A getAnnotation(Member m, Class<A> a){    // signature or name, false: ref
        if (m.getClass().isAssignableFrom(AnnotatedElement.class)) return ((AnnotatedElement) m).getAnnotation(a);
        return null;
    }
    
    public Member getMember(String signatureOrName, boolean stu){    // signature or name, false: ref
        Executable e = stu ? stuAllE.get(signatureTrafoS(signatureOrName)) 
                           : refChkE.get(signatureTrafoR(signatureOrName));
        return (e!=null) ? e : (stu ? stuAllF : refChkF).get(signatureOrName); // if it was no Executable we have to look into the fields
    }
    
    public String getMemberType(Member m){
        if (m.getClass().isAssignableFrom(Field      .class)) return "die Variable";
        if (m.getClass().isAssignableFrom(Method     .class)) return "die Methode";
        if (m.getClass().isAssignableFrom(Constructor.class)) return "der Konstruktor";
        return "Member";
    }

    public Result checkModifiers(String signature){
        return checkModifiers(signature, null);
    }
    
    public Result checkModifiers(String signatureOrName, ConfigData cd){
        ContextInfo ctx = new ContextInfo(signatureOrName, "Modifiertest");
        cd = merge(cd);
        if (cd.dbg(1)) sopl("starting checkModifiers("+signatureOrName+", "+cd+")");
        
        if (!hasElems())     // check if stuSol can be used
            return new Result(false, ctx, hasElems_errorText(ctx));
        
        Member ms = getMember(signatureOrName, true);
        if (ms==null)
            return new Result(false, ctx, executableNotFound_errorText(ctx));
            
        Member mr = getMember(signatureOrName, false);
        if (mr==null)   // not to be checked -> existing modifiers in ms are correct by default!!!!
            return new Result(true, ctx, "ok, keine zu testenden Vorgaben zu "+signatureOrName+" gefunden"); 
        
        String meTy = getMemberType(ms);
        int msModif = ms.getModifiers();
        int mrModif = mr.getModifiers();
        if (msModif!=mrModif){  // have to check if there are other modifier settings allowed...
            
            Variants mv = getAnnotation(mr, Variants.class);
            if (mv == null) // there are no alternatives!
                return new Result(false, ctx, wrongModifierType_errorText(signatureOrName, "Modifiertest", meTy, mrModif, msModif)); 
            
            for (ModifierCheck mc : ModifierCheck.parse(mv.modifierChecks())){
                // sopl("checking "+mc);
                if (!mc.ok(msModif))
                    return new Result(false, ctx, wrongModifierType_errorText(signatureOrName, "Modifiertest", meTy, mc, msModif)); 
            }
        }
        
        return new Result(true, ctx, "ok, "+meTy+" "+signatureOrName+" hat den Modifiertest bestanden"); 
    }


    
    // ................................... Loops ...................................................


    public Result checkLoops(String signature, boolean mustHaveAny){
        return checkLoops(signature, mustHaveAny, null, null);
    }
    
    // mustHave=true , loopTypes==null or empty  : must     have any "for", "while", or "do" loop
    // mustHave=false, loopTypes==null or empty  : must NOT have any "for", "while", or "do" loop
    // mustHave=true , loopTypes==at least 1 type: must     have at least one of the given types
    // mustHave=false, loopTypes==at least 1 type: must NOT have any of the given types
 
    public Result checkLoops(String signature, boolean mustHave, String[] loopTypes, ConfigData cd){
        ContextInfo ctx = new ContextInfo(signature, "Schleifentest");
        cd = merge(cd);
        if (cd.dbg(1)) sopl("starting checkLoops("+signature+", "+cd+")");
        
        if (!hasElems())     // check if stuSol can be used
            return new Result(false, ctx, hasElems_errorText(ctx));
        
        Executable xs = stuAllE.get(signatureTrafoS(signature));
        if (xs==null)
            return new Result(false, ctx, executableNotFound_errorText(ctx));
            
        // Method mr is not needed here. We simply check the student code.
        Boolean uses = codeAnalyserStu.usesSuchLoop(ctx.signature, loopTypes); // signatureTrafoS(signature)
        if (uses==null){
            if (cd.dbg(3)) sopl("checkLoops("+signature+", "+cd+") failed calling usesSuchLoop"); // internal error -> assume ms to be ok:-)
        }
        else {
            if (uses!=mustHave)
                return new Result(false, ctx, badUseOfLoops_errorText(signature, "Schleifentest", mustHave, loopTypes)); 
        }
        
        return new Result(true, ctx, "ok, die Methode "+signature+" hat den Schleifentest bestanden"); 
    }

    // ................................... Genearal use (must vs. forbidden) ...................................................

    // supported meta keys:
    //  "loop"     : "for", "do", OR "while"
    //  "floating" : "double" "float"
    //  "recursion": the name of the method

    public Result checkUse(String signature, boolean mustHave, String key){
        return checkUse(signature, mustHave, key, null);
    }
    
    public Result checkUse(String signature, boolean mustHave, String givenKey, ConfigData cd){
        String ctxStr   = "Implementierungsansatztest";
        String demanded = givenKey;
        String[] keys   = { givenKey };
        switch (givenKey){
            case "loop":        demanded = "for, do ODER while";
                                keys     = new String[]{"for", "do", "while"};
            case "for":        
            case "while":      
            case "do":          ctxStr = "Schleifenverwendungstest";  break;
            

            case "floating":    demanded = "double ODER float";
                                keys     = new String[]{"double", "float"};
            case "double":     
            case "float":       ctxStr = "Gleitkommaverwendungstest"; break;
            
            case "recursion":   demanded = nameFromSignature(signature);
                                keys     = new String[]{demanded};
                                ctxStr = "Rekursionsverwendungstest"; break;
            
        }
        cd = merge(cd);
        if (cd.dbg(1)) sopl("starting checkUse("+signature+", "+cd+")");
        ContextInfo ctx = new ContextInfo(signature, ctxStr, cd);
        
        if (!hasElems())     // check if stuSol can be used
            return new Result(false, ctx, hasElems_errorText(ctx));
        
        Executable xs = stuAllE.get(signatureTrafoS(signature));
        if (xs==null)
            return new Result(false, ctx, executableNotFound_errorText(ctx));
            
        // Method mr is not needed here. We simply check the student code.
        Boolean uses = null;
        for (String key:keys){
            if (key==null)
                continue;
            uses = codeAnalyserStu.usesKey(signatureTrafoS(signature), key); 
            // note: uses!=false causes exception if uses==null -> we implement: uses==null||uses
            if (uses==null||uses)  //             uses=| true  | false // if uses is false, we have always to check the other values too!
                break;             //                --+-------+------
                                   // mustHave =  true | break | next  // mustHave and we found it    -> can stop; will return true
                                   // mustHave = false | break | next  // mustNotHave but we found it -> can stop; will return false 
        }
        if (uses==null){
            if (cd.dbg(1)) sopl("checkUse("+signature+", "+cd+") failed calling usesKey"); // internal error -> assume ms to be ok:-)
        }
        else {
            if (uses!=mustHave)
                return new Result(false, ctx, badUseOfKey_errorText(signature, ctxStr, mustHave, demanded)); 
        }
        
        return new Result(true, ctx, "ok, die Methode "+signature+" hat den "+ctxStr+" bestanden"); 
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //   
    //  Some auxiliary methods 
    // 
    public boolean hasSameElement(String[] a, String[] b){
        for (String x: a)
            for (String y: b)
                if (x.equals(y))
                    return true;
        return false;
    }
    
    public static double difference(Object a, Object b){ // NOTE: a-b is important for the sign -> do not change this order!!!
        return (((Number) a).doubleValue()) - (((Number) b).doubleValue());
    }
    
    public static boolean isStatic(Member m){
        // see e.g. https://docs.oracle.com/javase/8/docs/api/constant-values.html#java.lang.reflect.Modifier.PUBLIC
        return m!=null && (m.getModifiers() & java.lang.reflect.Modifier.STATIC) != 0;
    }
    
    public static boolean isConstructor(Member m){
        // sopl("isConstructor: >>>"+m.getName()+"<<<>>>"+m.getDeclaringClass().getName()+"<<<");
        return m!=null && (m.getName().equals(m.getDeclaringClass().getName()));
    }
    
    public boolean isConstructor(String signature){
        // sopl("isConstructor("+signature+"): >>>"+signature.substring(0, signature.indexOf("("))+"<<<>>>"+stuSol.getName()+"<<<");
        // OR: signature.indexOf(clsNam)==0 && signature.charAt(clsNam.length())=='(' (assuming normalized signature without spaces etc)
        return signature!=null && (signature.substring(0, signature.indexOf("(")).equals(clsNam));
    }
    
    public static boolean isFloating(Object o){
        final Class<?> ty = o==null ? null : o.getClass();
        return ty==Double.class || ty==Float.class; // since o can not store primitives we do not need to check:  || ty==double.class || ty==float.class;
    }
    
    public static Object epsRoundIfFloating(Object o, double eps){
        // sopl("epsRoundIfFloating_dbg("+o+", "+eps+") cls:"+o.getClass()+" | "+isFloating(o));
        
        if (eps<=0 || !isFloating(o)) 
            return o;
            
        int digits = -(int)Range.round(Math.log10(eps), 0);
        final Class<?> ty = o==null ? null : o.getClass(); 
        // note: Object o is never double.class or float.class! Furthermore the returned Object is also never double or float -> valueOf is not needed!
//         if (ty==double.class) return                Range.round(((Number) o).doubleValue(), digits) ;
//         if (ty==Double.class) return Double.valueOf(Range.round(((Number) o).doubleValue(), digits));
//         if (ty== float.class) return                Range.round(((Number) o). floatValue(), digits) ;
//         if (ty== Float.class) return  Float.valueOf(Range.round(((Number) o). floatValue(), digits));
        if (ty==Double.class) return Range.round(((Number) o).doubleValue(), digits);
        if (ty== Float.class) return Range.round(((Number) o). floatValue(), digits);
        
        return o; // should never happen;-)
    }
    
    public boolean isEqual(Object a, Object b){ 
        return isEqual(a, b, null);
    }
    
    // NOTE: this is a "soft" equality test using epsilon (when possible) and without type checks (only arrays are handles specially)
    public boolean isEqual(Object a, Object b, ConfigData cd){ 
        
        // 1. Check if at least one is null
        if (a==null || b==null)
            return a==b;    // true only if both are null

        Class<?> ta = a.getClass(); 
        Class<?> tb = b.getClass(); 

        // 2. check if there is an array involved
        if (ta.isArray() || tb.isArray()){      // have to iterate over the elements
            if (ta.isArray() != tb.isArray())
                return false;                   // both must be arrays to be equal!

            int lenA = Array.getLength(a);
            int lenB = Array.getLength(b);
            if (lenA!=lenB)
                return false;                   // both arrays must have the same length to be equal!

            for (int i=0; i<lenA; i++)          // now we compare all the elements
                if (!isEqual(Array.get(a, i), Array.get(b, i), cd)) // recursion, depth should be not a problem (is equal to array dimension -> often 1 or 2)
                    return false;
            return true;                        // both arrays are equal
        }

        // 3. try to make an epsilon based test, if an epsilon value is defined and at least a or b is a floating point value
        // sopl("isEqual: "+isFloating(a)+", "+isFloating(b)+", "+cd);
        double floatEps = cd==null || cd.floatEps==null ? 0 : cd.floatEps;
        if (floatEps!=0 && (isFloating(a) || isFloating(b))){ // should perform epsilon test
            try {
                return Math.abs(difference(a, b))<=floatEps;
            } catch (Exception e){
                sopl("WARNING in isEqual("+a+", "+b+"): Sorry, could not apply epsilon based test for floating point value(s).");
            }
        }

        // 4. may be we have to compare two object instances, then we have 2 cases:
        //   4a) object has target type (i.e. generated by target constructor) -> one (usually b) will be of type ..._refSolClass
        //         -> have to allow that difference!
        //   4b) "normal" case where no transformation is needed <- will be seldom in our tests in AuP (?)
        // all fields of the objects are compared
        if (refSolStuSolEquality(ta, tb)){ // currently we handle only 4a)! 4b) is a todo!!!
            Object objR = ta.equals(refSol) ? a : b;
            Object objS = tb.equals(refSol) ? a : b;
            
            // iterate over all fields in oR which are to be checked
            for (String nam: refChkF.keySet()) {
                // Field fs = null; try { fs = objS.getClass().getField(fr.getName()); } catch (Exception e){}
                Field fr = refChkF.get(nam);  // try to get Field in objR with the name from the key set
                Field fs = stuAllF.get(nam);  // try to get Field in objS with the same name
                if (fs==null)
                    return false; // field not found in student solution
                
                fr.setAccessible(true);
                fs.setAccessible(true);
                Object valR = null; try { valR = fr.get(objR); } catch (Exception e){} // e.g. IllegalAccessException.
                Object valS = null; try { valS = fs.get(objS); } catch (Exception e){}
                if (!isEqual(valR, valS, cd))
                    return false; // values differ!
            }
            
            return true;
        }

        // 5. compare the values string based. This solves the Long(0)!=Integer(0) problem, BUT note:
        //      - "1" will be equal to some number with value 1
        //      - '1' is not equal to 0x31, but  '1'==0x31 is true
        //      - "true" == true
        //      - ...
        //    => May be ok or even very good, if type was not explicitely given in the task
        //    => Could perform extra type checks where needed (todo?)
        //    => We could also try to control the behaviour of the following equality test using ConfigData cd were necessary
        //       and switch between string based and number tests etc  
        return (""+a).equals(""+b); // simple implementation, may be that is too simple for special cases;-)
    }
    
    public static int compare(Object a, Object b){ 
        return compare(a, b, null);
    }
    
    public static int compare(Object a, Object b, ConfigData cd){ // returns -1 if a is smaller, 0 if both are equal, and +1 if b is smaller
        
        // 1. Check if at least one is null
        if (a==null || b==null)
            return a==b ? 0 : (a==null ? -1 : +1);    // 0 only if both are null (then they are equal); else we say "null is smaller" than existing objects

        Class<?> ta = a.getClass(); 
        Class<?> tb = b.getClass(); 

        // 2. check if there is an array involved
        if (ta.isArray() || tb.isArray()){      // have to iterate over the elements
            if (ta.isArray() != tb.isArray())
                return tb.isArray() ? -1 : +1;    // we say: "the non-array is smaller"

            int lenA = Array.getLength(a);
            int lenB = Array.getLength(b);
            if (lenA!=lenB)
                return lenA<lenB ? -1 : +1;       // we say: "the shorter is smaller"

            for (int i=0; i<lenA; i++){              // now we compare all the elements
                int c = compare(Array.get(a, i), Array.get(b, i), cd); // recursion, depth should be not a problem (is equal to array dimension -> often 1 or 2)
                if (c!=0) 
                    return c;   // found the first different element -> can stop here
            }
            return 0;                        // both arrays are equal
        }

        // 3. try to make an epsilon based test, if an epsilon value is defined and at least a or b is a floating point value
        // sopl("compare: "+isFloating(a)+", "+isFloating(b)+", "+cd);
        double floatEps = cd==null || cd.floatEps==null ? 0 : cd.floatEps;
        if (floatEps!=0 && (isFloating(a) || isFloating(b))){ // should perform epsilon test
            try {
                double a_minus_b = difference(a, b);  // a-b
                if ( Math.abs(a_minus_b)<=floatEps)
                    return 0;   // equal with respect to eps
                return a_minus_b < 0 ? -1 : +1; // as we know it from compareTo
            } catch (Exception e){
                sopl("WARNING in compare("+a+", "+b+"): Sorry, could not apply epsilon based test for floating point value(s).");
            }
        }
        
        // 4. try to compare numbers (withhout eps)
        try {
            if (isFloating(a) || isFloating(b)){
                double a_minus_b = difference(a, b);  // a-b
                return a_minus_b<0 ? -1 : (a_minus_b>0 ? +1 : 0);
            }
            // both are no floats -> make an integer comparizon (based on long!)
            long longA = ((Number)a).longValue();
            long longB = ((Number)b).longValue();
            return longA<longB ? -1 : (longA>longB ? +1 : 0); // ok, if we are here we had 2 numbers:-)
        } catch (Exception e) {} // ok, we simply continue if this was not possible

        // 5. compare the values string based. This solves the Long(0)!=Integer(0) problem, BUT note:
        //      - "1" will be equal to some number with value 1
        //      - '1' is not equal to 0x31, but  '1'==0x31 is true
        //      - "true" == true
        //      - ...
        //    => May be ok or even very good, if type was not explicitely given in the task
        //    => Could perform extra type checks where needed
        //    => We could also try to control the behaviour of the following compareTo test using ConfigData cd were necessary
        //       and switch between string based and number tests etc  
        return (""+a).compareTo(""+b); // simple implementation, may be that is too simple for special cases;-)
    }
    
    public static boolean hasAnnotation(AccessibleObject m, Class<? extends Annotation> a){  // AccessibleObject is the shared superclass for the common functionality of Field, Method, and Constructor.
        return m.getAnnotation(a) != null;
    }

    public static String calcSignature(Executable x){ // Executable is the shared superclass for the common functionality of Method and Constructor.
        return x==null ? "null" : calcSignature(x.getName(), x.getParameterTypes(), false); // false: not forHumans
    }

    public static String calcSignature(Executable x, boolean forHumans){ // Executable is the shared superclass for the common functionality of Method and Constructor.
        return x==null ? "null" : calcSignature(x.getName(), x.getParameterTypes(), forHumans);
    }

    public static String calcSignature(String xName, Class[] ca, boolean forHumans){
        return xName+typeParamList(ca, forHumans);
    }

    public static String typeParamList(Class[] a, boolean forHumans){
        return typeArrayToList(a, "(", ",", ")", forHumans);
    }

    public String objParamList(Object[] a){
        String s=a2s(a);
        return "("+s.substring(1, s.length()-1)+")";  // replace surrounding {} by ()
    }

    public static int arrDim(Object o){     // will return -1 in cas of non-existence and 0 in case it is no array (but does exist)
        return o==null ? -1 : arrDim(o.getClass());
    }
    
    public static int arrDim(Class<?> ty){  // will return -1 in cas of non-existence and 0 in case it is no array (but does exist)
        return ty==null ? -1 : (ty.isArray() ? (1+arrDim(ty.getComponentType())) : 0); // nice application of recursion to count dimensions
    }
    
    public static long arrValNum(Object o){ // per default we want to count null-values too, because they are shown as cd.nullStrInAsciiArt which is a certain character (length>0)
        return arrValNum(o, true);          // if you do not show null values (e.g. cd.nullStrInAsciiArt==""), then you may want to exclude null values from counting...
    }
    
    public static long arrValNum(Object o, boolean countNullValsToo){ // may be we want to count null values in a Cheracter[][] too (or not)
        if (o==null || !o.getClass().isArray())
            return 0;
         
        boolean is1DimArr = !o.getClass().getComponentType().isArray();
        long n = 0; // it is an array -> we have to count the number of stored values
        int len =  Array.getLength(o); 
        for (int i=0; i<len; i++){  
            Object obj = Array.get(o, i);
            if (obj!=null)
                n += obj.getClass().isArray() 
                  ? arrValNum(obj, countNullValsToo)    // count the values in the array and add it to n
                  : 1;                                  // note: each existing non-array object is a single value we have to count
            else
                if (countNullValsToo && is1DimArr)      // if the type o has itself arrays as components, then we can not 
                    n++;                
        }
        return n;
    }
    
    public String asString(Object o){ // asDbgString
        return asString(o, null);
    }
    
    public String asString(Object o, ConfigData cd){ // asDbgString
        if (o==null)
            return "null";
        final Class<?> ty = o.getClass();
        if (ty.isArray()){  // seems to be an array  -> iterate over the elements
            int lenO = Array.getLength(o);      
            
            // 1. check if special handling for generation of tabular output or even ASCII-art should be applied
            if (             cd != null     // such special behaviour must be switched on by ConfigData
                &&    arrDim(o) == 2        // we need 2 dimensions for tables or ASCII-art
                &&         lenO >  0        // empty arrays are hard to be recognized by students -> there must be at least one line
                && arrValNum(o) >  0        // but even if there are one or more lines: inner arrays may not exist or be empty or have only non-existing elements
               ){ // try to show tabular output or ASCII-art
    
                // 1a) use ASCII-art? NOTE: ASCII-art will also be shown for arrays with different inner length!
                if (cd.char2dimAsciiArt && (ty==char[][].class || ty==Character[][].class)){
                    StringBuilder r = new StringBuilder().append("\n"); // we start with a new line to see the ASCII-art always correctly (hopefully this make no problems;-) 
                    for (int idxO=0; idxO<lenO; idxO++){                // iterate over all the indices idxO in the outer array
                        Object ia = Array.get(o, idxO);                 // the inner array
                        if (ia!=null){                                  // when the inner array exists, we have to print all its existing chars
                            int lenI =  Array.getLength(ia);            // the length of the inner char/Character array, note: in general there may be different array length!
                            for (int idxI=0; idxI<lenO; idxI++){  
                                Object chr = Array.get(ia, idxI);
                                r.append(chr!=null ? chr : cd.nullStrInAsciiArt);
                            }
                        }
                        r.append("\n");                             // a new line is always needed (even if ia does not exist)
                    }
                    return r.toString();
                }
                
                // 1b) show values as table -> use formating per value, may be also rounding -> same width for each value string... TODO
                
            }
        
            // 2. normal case -> e.g. all in one line or with indentation ...   todo -> use defaults from ConfigData for bef, sep and aft; may be controlled by current dimension 
            StringBuilder r = new StringBuilder(); 
            for (int i=0; i<lenO; i++)
                r.append(r.length()==0?"":", ").append(asString(Array.get(o, i), cd)); 
            return "{"+r.toString()+"}";
        }

        if (!ty.equals(String.class) && !ty.isPrimitive()){
            if (ty.equals(refSol) || ty.equals(stuSol)){
                // following did not work with nice toString() :-(
                // String r=(""+o).replaceAll("@[a-f0-9A-F]+$", "") // Rechteck@3d646c37 -> 
                //                .replaceAll("_refSolClass", "");
                String r=clsNam; // simple and robust !
                String v="";
                for (String nam: refChkF.keySet()) {
                    Field f = (ty.equals(refSol) ?refChkF:stuAllF).get(nam);  // try to get Field in objS with the same name
                    if (v.length()>0)
                        v+="; ";
                    v+=nam;
                    if (f==null)
                        v+=" nicht vorhanden!"; // field not found in student solution
                    else
                        try {   f.setAccessible(true); v+= "="+asString(f.get(o));} 
                        catch (Exception e){ v+=" nicht lesbar! "+e+" in \n"+toString(); }
                }
                return r+(v.length()==0?v:(" {"+v+"}"));
            }
        }
        
        return    (cd==null || cd.quoteCharInErr  ) && (ty==Character.class || ty==char.class) ? ( "'"+o+"'") 
               : ((cd==null || cd.quoteStringInErr) &&  ty==String.class                       ? ("\""+o+"\"")                         
               :                                                                                (   ""+o    ));
    }
    
    public static String asStringStatic(Object o){ // asDbgString
        return asStringStatic(o, null);
    }
    
    public static String asStringStatic(Object o, ConfigData cd){ // asDbgString
        if (o==null)
            return "null";
        final Class<?> ty = o.getClass();
        if (ty.isArray()){  // seems to be an array  -> iterate over the elements
            int lenO = Array.getLength(o);      
            
            // 1. check if special handling for generation of tabular output or even ASCII-art should be applied
            if (             cd != null     // such special behaviour must be switched on by ConfigData
                &&    arrDim(o) == 2        // we need 2 dimensions for tables or ASCII-art
                &&         lenO >  0        // empty arrays are hard to be recognized by students -> there must be at least one line
                && arrValNum(o) >  0        // but even if there are one or more lines: inner arrays may not exist or be empty or have only non-existing elements
               ){ // try to show tabular output or ASCII-art
    
                // 1a) use ASCII-art? NOTE: ASCII-art will also be shown for arrays with different inner length!
                if (cd.char2dimAsciiArt && (ty==char[][].class || ty==Character[][].class)){
                    StringBuilder r = new StringBuilder().append("\n"); // we start with a new line to see the ASCII-art always correctly (hopefully this make no problems;-) 
                    for (int idxO=0; idxO<lenO; idxO++){                // iterate over all the indices idxO in the outer array
                        Object ia = Array.get(o, idxO);                 // the inner array
                        if (ia!=null){                                  // when the inner array exists, we have to print all its existing chars
                            int lenI =  Array.getLength(ia);            // the length of the inner char/Character array, note: in general there may be different array length!
                            for (int idxI=0; idxI<lenO; idxI++){  
                                Object chr = Array.get(ia, idxI);
                                r.append(chr!=null ? chr : cd.nullStrInAsciiArt);
                            }
                        }
                        r.append("\n");                             // a new line is always needed (even if ia does not exist)
                    }
                    return r.toString();
                }
                
                // 1b) show values as table -> use formating per value, may be also rounding -> same width for each value string... TODO
                
            }
        
            // 2. normal case -> e.g. all in one line or with indentation ...   todo -> use defaults from ConfigData for bef, sep and aft; may be controlled by current dimension 
            StringBuilder r = new StringBuilder(); 
            for (int i=0; i<lenO; i++)
                r.append(r.length()==0?"":", ").append(asStringStatic(Array.get(o, i), cd)); 
            return "{"+r.toString()+"}";
        }

        return    (cd==null || cd.quoteCharInErr  ) && (ty==Character.class || ty==char.class) ? ( "'"+o+"'") 
               : ((cd==null || cd.quoteStringInErr) &&  ty==String.class                       ? ("\""+o+"\"")                         
               :                                                                                (   ""+o    ));
    }
 
    public String a2s(Object a){ // only a shorter name for asString -> may be used also as "array to string"-method for any kind and dimension of array
        return asString(a, null);
    }
 
//  following type check is not needed!    
//     public static String a2s(byte   []     a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(byte        x:a) r.append(r.length()==0?"":", ").append(    x ); return "{"+r.toString()+"}"; }
//     public static String a2s(short  []     a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(short       x:a) r.append(r.length()==0?"":", ").append(    x ); return "{"+r.toString()+"}"; }
//     public static String a2s(int    []     a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(int         x:a) r.append(r.length()==0?"":", ").append(    x ); return "{"+r.toString()+"}"; }
//     public static String a2s(long   []     a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(long        x:a) r.append(r.length()==0?"":", ").append(    x ); return "{"+r.toString()+"}"; }
//     public static String a2s(float  []     a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(float       x:a) r.append(r.length()==0?"":", ").append(    x ); return "{"+r.toString()+"}"; }
//     public static String a2s(double []     a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(double      x:a) r.append(r.length()==0?"":", ").append(    x ); return "{"+r.toString()+"}"; }
//     public static String a2s(char   []     a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(char        x:a) r.append(r.length()==0?"":", ").append(    x ); return "{"+r.toString()+"}"; }
//     public static String a2s(boolean[]     a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(boolean     x:a) r.append(r.length()==0?"":", ").append(    x ); return "{"+r.toString()+"}"; }
//     public static String a2s(Object []     a){ return asString(a); }
// 
//     public static String a2s(byte   [][]   a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(byte   []   x:a) r.append(r.length()==0?"":", ").append(a2s(x)); return "{"+r.toString()+"}"; }
//     public static String a2s(short  [][]   a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(short  []   x:a) r.append(r.length()==0?"":", ").append(a2s(x)); return "{"+r.toString()+"}"; }
//     public static String a2s(int    [][]   a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(int    []   x:a) r.append(r.length()==0?"":", ").append(a2s(x)); return "{"+r.toString()+"}"; }
//     public static String a2s(long   [][]   a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(long   []   x:a) r.append(r.length()==0?"":", ").append(a2s(x)); return "{"+r.toString()+"}"; }
//     public static String a2s(float  [][]   a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(float  []   x:a) r.append(r.length()==0?"":", ").append(a2s(x)); return "{"+r.toString()+"}"; }
//     public static String a2s(double [][]   a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(double []   x:a) r.append(r.length()==0?"":", ").append(a2s(x)); return "{"+r.toString()+"}"; }
//     public static String a2s(char   [][]   a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(char   []   x:a) r.append(r.length()==0?"":", ").append(a2s(x)); return "{"+r.toString()+"}"; }
//     public static String a2s(boolean[][]   a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(boolean[]   x:a) r.append(r.length()==0?"":", ").append(a2s(x)); return "{"+r.toString()+"}"; }
// 
//     public static String a2s(byte   [][][] a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(byte   [][] x:a) r.append(r.length()==0?"":", ").append(a2s(x)); return "{"+r.toString()+"}"; }
//     public static String a2s(short  [][][] a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(short  [][] x:a) r.append(r.length()==0?"":", ").append(a2s(x)); return "{"+r.toString()+"}"; }
//     public static String a2s(int    [][][] a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(int    [][] x:a) r.append(r.length()==0?"":", ").append(a2s(x)); return "{"+r.toString()+"}"; }
//     public static String a2s(long   [][][] a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(long   [][] x:a) r.append(r.length()==0?"":", ").append(a2s(x)); return "{"+r.toString()+"}"; }
//     public static String a2s(float  [][][] a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(float  [][] x:a) r.append(r.length()==0?"":", ").append(a2s(x)); return "{"+r.toString()+"}"; }
//     public static String a2s(double [][][] a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(double [][] x:a) r.append(r.length()==0?"":", ").append(a2s(x)); return "{"+r.toString()+"}"; }
//     public static String a2s(char   [][][] a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(char   [][] x:a) r.append(r.length()==0?"":", ").append(a2s(x)); return "{"+r.toString()+"}"; }
//     public static String a2s(boolean[][][] a){if (a==null) return "null"; StringBuilder r=new StringBuilder(); for(boolean[][] x:a) r.append(r.length()==0?"":", ").append(a2s(x)); return "{"+r.toString()+"}"; }

    public static String removeDblQuotes(String s){
        if (s.equals("null"))
            return null;
        if (s.length()>=2 && s.charAt(0)=='"' && s.charAt(s.length()-1)=='"')
            return s.substring(1,s.length()-1);
        throw new NumberFormatException();
    }

    public static Pattern pat_removeSglQuotes = Pattern.compile("^'(.)'$", Pattern.DOTALL); 
    public static String removeSglQuotes(String s){
        Matcher m = pat_removeSglQuotes.matcher(s);
        if (m.matches())
            return m.group(1);
        sopl("Error: removeSglQuotes("+s+") failed");
        return s;
    }

    public static Pattern pat_parseSingleQuotedChar = Pattern.compile("^'(.)'$", Pattern.DOTALL); 
    public static Character parseSingleQuotedChar(String s){
        Matcher m = pat_parseSingleQuotedChar.matcher(s);
        if (m.matches())
            return m.group(1).charAt(0);
        sopl("Error: parseSingleQuotedChar("+s+") failed");
        return null;
    }

    public static Pattern pat_removeCurlyBrks = Pattern.compile("^\\{(.*)\\}$", Pattern.DOTALL); 
    public static String removeCurlyBrks(String s){
        Matcher m = pat_removeCurlyBrks.matcher(s);
        if (m.matches())
            return m.group(1);
        sopl("Error: removeCurlyBrks("+s+") failed");
        return s;
    }

    public static Object objIfNeeded(Class<?> c, Member m){
        if (isStatic(m)||isConstructor(m))
            return null; // no object needed
        try {
            try {   // hopefully there is a default constructor
                    // getDeclaredConstructor() is important to avoid : "newInstance() in Class has been deprecated"
                return c.getDeclaredConstructor().newInstance();
            } catch (Exception e){}
            
            // seems as we do not have a default constructor! -> have to use an other one
            // sopl("----------------- objIfNeeded("+c+", "+m.getName()+") found no defCon!");
            for (Constructor con:c.getDeclaredConstructors()){
                Class<?>[] ta = con.getParameterTypes();
                Object[] oa = new Object[ta.length];
                for (int i=0; i<ta.length; i++)
                    oa[i] = typedDefaultValue(ta[i]);
                return con.newInstance(oa);    
            }
            return null;
        }
        catch (Exception e){
            sopl("Could not generate object from class "+c+". Thus the non-static method "+m+" can not be called.");
            return null;
        }
    }

    public static String setToDbgLines(Set<?> s){
        StringBuilder ret = new StringBuilder();
        for(Object o:s)
            ret.append("\n        ").append(o);
        return ret.toString();
    }

    public static Class[] objs2types(Object[] a){
        if (a==null)
            return null;
        Class[] ret = new Class[a.length];
        for(int i=0; i<a.length; i++)
            ret[i] = a[i]==null ? null : a[i].getClass();
        return ret;
    }

    public static String typeArrayToList(Class[] a, String bef, String sep, String aft, boolean forHumans){
        StringBuilder ret = new StringBuilder().append(bef);
        for(int i=0; i<a.length; i++)
            ret.append(i==0?"":sep).append(forHumans ? signatureParamTypeTrafo_java2human(""+a[i]) : a[i]);
        return ret.append(aft).toString();
    }

    public static String scopeArrayToList(String[] a, String bef, String sep, String aft){
        if (a==null || a.length==0)
            return null;    // stands for: no scope
        StringBuilder ret = new StringBuilder().append(bef);
        for(int i=0; i<a.length; i++)
            ret.append(i==0?"":sep).append(a[i]);
        return ret.append(aft).toString();
    }

    public static String variantsArrayToList(String[] a, String empty, String sep, String lastSep){
        if (a==null || a.length==0)
            return empty;
        StringBuilder ret = new StringBuilder().append(a[0]);
        for(int i=1; i<a.length; i++)
            ret.append(i==a.length-1?lastSep:sep).append(a[i]);
        return ret.toString();
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //  splitValueList: a method to split e.g. a parameter list into the single parameters with correct handling of , in strings etc
    //    - There is one string for each parameter in the returned array.
    //    - A parameter can be:
    //        * a single quoted char (incl. / escaped chars)
    //        * a double quotetd string (incl. / escaped chars; an empty string is also allowed)
    //        * some chars excluding: ,{}"'
    //        * an array (one or more dimensions) beginning with { and ending with }
    public static String[] splitValueList(String s){
        ArrayList<String> al = new ArrayList<String>();
        int n=s.length();
        int p=-1;
        int strBeg = -1; // not in a string, it is the position of the begin "
        int chrBeg = -1; // not in a char, it is the position of the begin '
        int parBeg = -1; // not in a part, it is the position of the begin of a part we found during parsing
        int arrBeg = -1; // not in an array in level 0, it is the position of the begin of a part we found during parsing
        int lstEsc = -1; // position of the last AND ACTIVE \ char
        int arrLev =  0; // not in an array - but in the parameter list, inc for each { and dec for each } if those are not in a string or char
        boolean commaOrEndNeeded = false;
        
        while (++p<n){
            if (lstEsc<p-1 && chrBeg<0)     // in char mode the escape char stays active
                lstEsc=-1;                  // became inactive now

            char c=s.charAt(p);
                
            if (c=='\\'){
                if (lstEsc>=0){
                    lstEsc=-1;      // \\ found -> the current escape is not active
                    continue;       // continue to collect part, string or char
                }
                lstEsc=p;
                continue;
            }
            
            if (strBeg>=0){ // we are in a string and collect chars until we find a "
                if (c=='"' && lstEsc<0){ // end of String found
                    if (arrLev==0) al.add(s.substring(strBeg, p+1)); // incl. ""
                    strBeg=-1;
                    commaOrEndNeeded = true;
                }
                continue;
            }
            
            if (chrBeg>=0){ // we are in a char and hope/check that there is only one char until the next ' or \ followed by a 2nd char
                            // TODO: decide if \ recognition is realy needed, because in Java we have after comiling no extra char \ anymore!
                            //        ... and in Strings...????  -> we need more practical experiences to decide! 
                if (lstEsc<0 && c=='\'' && p-chrBeg == 1)
                    throw new NumberFormatException("There must be a char between '' "+splitValueList_posStr(p, s));
                if (p-chrBeg-(lstEsc<0?0:1) >= 2){
                    if (c!='\'')
                        throw new NumberFormatException("There must be a closing ' after '"+c+" "+splitValueList_posStr(p, s));
                    if (arrLev==0) al.add(s.substring(chrBeg, p+1)); // incl. '' -> e.g. 'c' or possibly '\'' or '\n'
                    chrBeg=-1;
                    commaOrEndNeeded = true;
                } // else we continue to collect
                continue;
            }
            
            if (commaOrEndNeeded){
                if (Character.isWhitespace(c))
                    continue;
                if (c!='}'){
                    if (c!=',')
                        throw new NumberFormatException("There must be a comma "+splitValueList_posStr(p, s));
                    commaOrEndNeeded=false;
                    continue; // ok, we had our comma
                } // else: } is also a kind of end and will be handled below 
            }

            if (c=='{'){ // begin array
                if (arrLev==0)
                    arrBeg=p;
                arrLev++;
                continue;
            }

            if (c=='}'){ // end array
                arrLev--;
                if (arrLev<0)
                    new NumberFormatException("Without having an opening { the } tries to close an array "+splitValueList_posStr(p, s));
                if (arrLev==0){
                    al.add(s.substring(arrBeg, p+1).trim()); // incl. {}
                    arrBeg=-1;
                    parBeg=-1; // else we would flush it at the end again
                }
                commaOrEndNeeded = true;
                continue;
            }

            if (c==','){ // should be the end of a part
                if (parBeg<0)
                    throw new NumberFormatException("There must be a value before the comma "+splitValueList_posStr(p, s));
                if (arrLev==0) al.add(s.substring(parBeg, p).trim()); // not incl. , and spaces
                parBeg=-1;
                continue;
            }
            
            if (c=='\''){ 
                if (parBeg>=0)
                    throw new NumberFormatException("There must not be chars befor the ' "+splitValueList_posStr(p, s));
                chrBeg=p; 
                continue; 
            }

            if (c=='"'){ 
                if (parBeg>=0)
                    throw new NumberFormatException("There must not be chars befor the \" "+splitValueList_posStr(p, s));
                strBeg=p; 
                continue; 
            }

            if (!Character.isWhitespace(c) && parBeg<0)
                parBeg=p;
        }

        if (chrBeg>=0) throw new NumberFormatException("There must be a closing ' for the ' "  +splitValueList_posStr(chrBeg, s));
        if (strBeg>=0) throw new NumberFormatException("There must be a closing \" for the \" "+splitValueList_posStr(strBeg, s));
        if (arrBeg>=0) throw new NumberFormatException("There must be a closing } for the { "  +splitValueList_posStr(arrBeg, s));
        if (parBeg>=0) al.add(s.substring(parBeg, p).trim()); // flush the last value
        
        String[] r = new String[al.size()];
        for (int i=0; i<r.length; i++)
            r[i]=al.get(i); 
        return r;
    }
    
    public static String splitValueList_posStr(int p, String s){
        String e=""; while (e.length()<p) e+=" ";
        return "at position "+(p+1)+" in the parameter list:\n"+s+"\n"+e+"^";  // infact p is an index and p+1 makes it to a position ;-)  
    }

    static public Object[] objArrFromParamStr(Class[] tyArr, String s){ // ta is the type array which was extracted from the signature
        String[] esa = splitValueList(s);       // the element string array
        if (tyArr.length != esa.length)
             throw new NumberFormatException("ERROR in objArrFromParamStr() : type array length="+tyArr.length+" differs from parameter list length="+esa.length);

        Object[] ret = new Object[esa.length];
        for (int i=0; i<ret.length; i++)
            ret[i] = parseTypedValue(tyArr[i], esa[i]);
        return ret;
    }
    
    static public Object arrFromArrayStr(Class ty, String s){ // ty is the array type; NOTE: int[] can be seen as Object, but not as Object[] -> we return Object type 
        String[] esa = splitValueList(s);       // the element string array
        Class<?> eTy = ty.getComponentType();   // same for all elements in Java

        Object ret = Array.newInstance(eTy, esa.length); 
        for (int i=0; i<esa.length; i++)
            Array.set(ret, i, parseTypedValue(eTy, esa[i]));
        return ret;
    }
    
    static public Object typedDefaultValue(Class<?> ty){            
        if (ty==     byte.class) return (   byte)0;
        if (ty==    short.class) return (  short)0;
        if (ty==      int.class) return (    int)0;
        if (ty==     long.class) return (   long)0;
        if (ty==    float.class) return (  float)0;
        if (ty==   double.class) return ( double)0;
        if (ty==     char.class) return (   char)0;
        if (ty==  boolean.class) return false;
        return null;
    }

    static public Object parseTypedValue(Class<?> ty, String s){            
        s = s.trim();
        
        if (ty==     byte.class) return               Range.pB(s.replaceAll("_", ""));
        if (ty==    short.class) return               Range.pS(s.replaceAll("_", ""));
        if (ty==      int.class) return               Range.pI(s.replaceAll("_", ""));
        if (ty==     long.class) return               Range.pL(s.replaceAll("_", ""));
        if (ty==    float.class) return               Range.pF(s                    );
        if (ty==   double.class) return               Range.pD(s                    );
        if (ty==     char.class) return  parseSingleQuotedChar(s                    );
        if (ty==  boolean.class) return               Range.pT(s                    );

        boolean N=s.equals("null");
        Object  n=null;
        
        if (ty==     Byte.class) return N?n:             Range.p_B(s.replaceAll("_", ""));
        if (ty==    Short.class) return N?n:             Range.p_S(s.replaceAll("_", ""));
        if (ty==  Integer.class) return N?n:             Range.p_I(s.replaceAll("_", ""));
        if (ty==     Long.class) return N?n:             Range.p_L(s.replaceAll("_", ""));
        if (ty==    Float.class) return N?n:             Range.p_F(s                    );
        if (ty==   Double.class) return N?n:             Range.p_D(s                    );
        if (ty==Character.class) return N?n: parseSingleQuotedChar(s                    );
        if (ty==  Boolean.class) return N?n:             Range.p_T(s                    );

        if (ty==   String.class) return N?n:       removeDblQuotes(s                    );// only removes surrounding ""

        if (ty.isArray())        return N?n: arrFromArrayStr(ty, s.substring(1, s.length()-1)); // removing brackets will crash if s.length() is too small (has no brackets) -> todo???
        
        throw new NumberFormatException("parseTypedValue("+ty+", "+s+") found unknown type!");
    }
    
    public static Pattern pat_stripJavaComments = Pattern.compile("//.*|/\\*((?s).*?)\\*/|((?<!')\"(?:(?<!\\\\)(?:\\\\\\\\)*\\\\\"|[^\r\n\"])*\")"); // note: 2 groups, but in kill mode group(1) is not used
    public static String stripJavaComments(String s, boolean keepLines){
        //      //.*                      1. part: removes line comments -> matches // and all chars until end of line
        //      |                        ------------ OR ---------------------------------------------------                                                                    
        //      /\*((?s).*?)\*/           2. part: removes block comments -> matches /* and */ and any chars between (inkl. \n because of (?s), unless keepLines==true -> then group(1) is used
        //      |                        ------------ OR ---------------------------------------------------
        //      ((?<!')"                  3. part: ( matches the begin of the string capturing group and " the start of the string. (?<!') ensures that we do not have a ' before the " like in  a='"'; // ups  "                                  
        //        (?:                              (?: starts a non-capturing group. In this group the "inner" chars of the captured string will be described now:
        //           (?<!\\)(?:\\\\)*\\"             3a: \\" matches an escaped " (because they do not stop a string) and (?<!\\)(?:\\\\)* allows any even number of \ before                
        //           |                                   or                                                                 
        //           [^\r\n"]                        3b: match any char except linebreaks and "                        
        //        )*                               ) is the end of the non-capturing group for the possible chars in the inner of a string and * says 0... chars a                                              
        //      ")                                 " matches the end of the string and ) closes the capturing group  
        StringBuffer r = new StringBuffer();
        Matcher m = pat_stripJavaComments.matcher(s==null ? "" : s);
        while(m.find())
            m.appendReplacement(r, m.group(1)!=null ? (keepLines ? m.group(1).replaceAll("[^\n\r]", ""): "") 
                                                    : m.group(2)!=null ? m.group(2) : "" );
        m.appendTail(r);
        return r.toString();
    }

    //public static Pattern pat_stripJavaStrings = Pattern.compile("\"[^\"]*\""); // note: 2 groups, but in kill mode group(1) is not used
    public static Pattern pat_stripJavaStrings = Pattern.compile("\"[^\"]*(?:(?:\\\\)*(?:\"[^\"]*)?)*\""); // Todo: there must not be an ' before "
    public static String stripJavaStrings(String s){
        StringBuffer r = new StringBuffer();
        Matcher m = pat_stripJavaStrings.matcher(s==null ? "" : s);
        int n=0;
        while(m.find())
            m.appendReplacement(r, " someRemovedStringConst"+(++n)+" "); 
        m.appendTail(r);
        return r.toString();
    }
    
    public static String tagSrc(String s){  // Tag a String with java code by generating a String with chars as labels.
                                            // At the begin of an error we begin to fill the tagSrc with letters 'E'.
                                            // We ensure that there is at least one 'E' if there is a non-closed string, char or block comment.
        char[] r = new char[s.length()];
        char m=' '; // neutral mode
        int beg = 0;
        int lft = r.length;
        for (int i=0; i<r.length; i++){
            lft--;
            char c=s.charAt(i);
            switch (m){
                case 'C':   // char begin mode
                            if (c=='\\'){   // handle escapes like '\x' and '\u123A'
                                if (lft>=2){ // we need at least 2 chars
                                    r[i++]=m;
                                    if (s.charAt(i)!='u'){ // handle '\x'
                                        r[i++]=m;
                                        r[i  ]=m;
                                        m=' ';
                                        continue;
                                    }
                                    else{ // handle '\u123A'
                                        r[i++]=m;
                                        if (lft>=6 && s.charAt(i+4)=='\''){ // we need at least 6 chars and ' as last
                                            boolean ok=true;
                                            int q=i+4;
                                            while (i<q){
                                                char d=s.charAt(i);
                                                if ((d<'0' || '9'<d) && (d<'a' || 'f'<d) && (d<'A' || 'F'<d)){
                                                    ok=false;
                                                    break;
                                                }
                                                r[i++]=m;
                                            }
                                            if (ok){
                                                r[i]=m; // set tag for ' at end
                                                m=' ';
                                                continue;
                                            }
                                        }
                                    }
                                }
                                // handle these error cases below ...
                            }
                            else { // no escape -> sholuld be an normal character 'x' -> check for ending '
                                if (i+1<r.length && s.charAt(i+1)=='\''){ // handle as normal char
                                    r[i++]=m; // leaving char mode now
                                    r[i  ]=m; // the char itself end ' are part of the char
                                    m=' ';
                                    continue;
                                } // else we have an error!
                            }
                            m='E';
                            break;
                            
                            
                case 'S':   // string mode
                            if (c=='\n'){
                                m='E';  // go into error mode (end of sting must be before end of line!)
                                break;  // leave in string mode, fill from beg with E
                            }
                            if (c!='"')
                                break; // stay in string mode
                            int t=i-1;
                            boolean isEnd=true;
                            while (t>beg && s.charAt(t--)=='\\')
                                isEnd=!isEnd;
                            if (!isEnd)
                                break;  // odd number of \ before found (0, 2, 4, 6, ...) -> stay in string mode    
                            r[i]=m; // " is the end but part of the string
                            m=' ';  // go into normal mode
                            continue;
     
                case '#':   // some comment begin mode -> look for / or *; overwrite last r[i]='#' to 'L', 'B' or ' ' 
                            switch (c){
                                case '/': m='L'; break;
                                case '*': m='B'; break;
                                default : m=' '; break;
                            }
                            r[i-1]=m;
                            break;
                            
                case 'L':   // line comment mode
                            if (c!='\n')
                                break; // stay in line comment
                            m=' ';
                            r[i]=m; // newline is not part of the comment -> good: when comments are removed then the newline is kept
                            continue;
                            
                case 'B':   // block comment mode
                            if (c=='\n'){
                                r[i]=' ';
                                continue;
                            }
                            if (c!='*' || i+1==r.length || s.charAt(i+1)!='/')
                                break; // stay in block comment mode
                            r[i++]=m; // leaving block comment now
                            r[i  ]=m; // chars */ are part of the comment 
                            m=' ';
                            continue;

                default :   // neutral mode  
                            switch(c){
                                case '\'':  // start char mode, we can detect '\u263a' | '\X' with X={t,r,n,',...} | 'X'    
                                             m=lft<2?'E':'C'; beg=i; break;
                                            
                                case '"':   // start string mode until we find a not escaped " at the end
                                             m=lft<1?'E':'S'; beg=i; break;
                                
                                case '/':   // may start some comment, we can detect // and /*, else it seems t obe division operator...
                                             m=lft<1?'E':'#'; beg=i; break;      
                            }
            }
            if (m=='E')
                break;
            r[i]=m;
        }
        
        if (m=='E' || m=='B' || m=='S')
            for (int e=beg; e<r.length; e++)
                r[e]=s.charAt(e)=='\n'?' ':'E'; // keep newlines
        return new String(r);
    }

    public static void sopl(String s){
        System.out.println(s);
    }
    
    public static void sop(String s){
        System.out.print(s);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    //   
    //  Experimental code 
    // 

    public void doCheck(){
        try {
            Method    m1 = refSol.getMethod("sumDoWhile", int.class, int.class);
            Variants mv = m1.getAnnotation(Variants.class);
            Annotation[] aa = m1.getAnnotations();
            System.out.println( "mv "+mv+"  aa:"+aa );
            if (mv != null){
                Class[] ot = mv.otherTypes();
                System.out.println("ot:"+ot );
            }
            if (aa!=null){
                System.out.println("Annotations found : "+aa.length);
                for (int i=0; i<aa.length; i++)
                    System.out.println("A"+i+" : "+aa[i]);
            }
        }
        catch (NoSuchMethodException e){
            System.out.println("Exception :"+e);
        }
    }
    
    public static void main(String[] args){
    }
}
