import java.util.*;
import static java.util.stream.Collectors.*;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.TerminalNode;

class Analysis extends LoopNestBaseListener {

    // Possible types
    enum Types {
        Byte, Short, Int, Long, Char, Float, Double, Boolean, String
    }

    // Type of variable declaration
    enum VariableType {
        Primitive, Array, Literal
    }

    // Types of caches supported
    enum CacheTypes {
        DirectMapped, SetAssociative, FullyAssociative,
    }

    // auxilliary data-structure for converting strings
    // to types, ignoring strings because string is not a
    // valid type for loop bounds
    final Map<String, Types> stringToType = Collections.unmodifiableMap(new HashMap<String, Types>() {
        private static final long serialVersionUID = 1L;

        {
            put("byte", Types.Byte);
            put("short", Types.Short);
            put("int", Types.Int);
            put("long", Types.Long);
            put("char", Types.Char);
            put("float", Types.Float);
            put("double", Types.Double);
            put("boolean", Types.Boolean);
        }
    });

    // auxilliary data-structure for mapping types to their byte-size
    // size x means the actual size is 2^x bytes, again ignoring strings
    final Map<Types, Integer> typeToSize = Collections.unmodifiableMap(new HashMap<Types, Integer>() {
        private static final long serialVersionUID = 1L;

        {
            put(Types.Byte, 0);
            put(Types.Short, 1);
            put(Types.Int, 2);
            put(Types.Long, 3);
            put(Types.Char, 1);
            put(Types.Float, 2);
            put(Types.Double, 3);
            put(Types.Boolean, 0);
        }
    });

    // Map of cache type string to value of CacheTypes
    final Map<String, CacheTypes> stringToCacheType = Collections.unmodifiableMap(new HashMap<String, CacheTypes>() {
        private static final long serialVersionUID = 1L;

        {
            put("FullyAssociative", CacheTypes.FullyAssociative);
            put("SetAssociative", CacheTypes.SetAssociative);
            put("DirectMapped", CacheTypes.DirectMapped);
        }
    });

    public Analysis() {
    }

    Map<String, Integer> symbolTable = new HashMap<String, Integer>();
    Map<String, Integer> arrayDims = new HashMap<String, Integer>();
    Map<String, Integer> arrayDatatypeBits = new HashMap<String, Integer>();

    int cacheType; //0 for DirectMapped, 1 for SetAssociative, 2 for FullyAssociative

    String exitVariableName;
    String exitVariableType;
    String exitVariableValue;

    int cacheLines;
    int sets = -1; //number of sets = [ DirectMapped = cacheLines, SetAssociative = setindex, FullyAssociative = 1 ]
    int nestingLevel = 0;

    Map<String, Integer> loopIndexLowerBound = new HashMap<String, Integer>();
    Map<String, Integer> loopIndexUpperBound = new HashMap<String, Integer>();
    Map<String, Integer> loopIndexStride = new HashMap<String, Integer>();
    Map<String, Integer> loopIndexLevel = new HashMap<String, Integer>();
    Map<String, Integer> blockElements = new HashMap<String, Integer>(); //ArrayName, no. of elements in block
    Map<String, Integer> arrayBlocks = new HashMap<String, Integer>(); //no. of blocks in the array
    Map<String, List<Integer>> arrayRowCol = new HashMap<String, List<Integer>>();
    Map<String, Long> cacheMisses = new HashMap<String, Long>();
    List< Map<String, Long> > missesList = new ArrayList< Map<String, Long> >();

    public <K, V> K getKey(Map<K, V> map, V value) {
    for (Map.Entry<K, V> entry : map.entrySet()) {
        if (entry.getValue().equals(value)) {
            return entry.getKey();
        }
    }
    return null;
  }

    @Override
    public void enterMethodDeclaration(LoopNestParser.MethodDeclarationContext ctx) {

        //System.out.println("__________________________________________________");
        //System.out.println("enterMethodDeclaration");
        //System.out.println("Text:  " + ctx.getText());
        //System.out.println();
    }

    public void exitMethodDeclaration(LoopNestParser.MethodDeclarationContext ctx) {
/*
        System.out.println("symbolTable");
        for (Map.Entry<String, Integer> entry : symbolTable.entrySet())
            System.out.println("Key = " + entry.getKey() +
                             ", Value = " + entry.getValue());

        System.out.println("arrayDims");
        for (Map.Entry<String, Integer> entry : arrayDims.entrySet())
            System.out.println("Key = " + entry.getKey() +
                             ", Value = " + entry.getValue());

        System.out.println("arrayDatatypeBits");
        for (Map.Entry<String, Integer> entry : arrayDatatypeBits.entrySet())
            System.out.println("Key = " + entry.getKey() +
                             ", Value = " + entry.getValue());

        System.out.println("blockElements");
        for (Map.Entry<String, Integer> entry : blockElements.entrySet())
            System.out.println("Key = " + entry.getKey() +
                             ", Value = " + entry.getValue());
        System.out.println("CacheType");
        System.out.println(cacheType);
*/

        Map<String, Long> tempMap = new HashMap<String, Long>();
        for (Map.Entry<String, Long> entry : cacheMisses.entrySet()) {
            tempMap.put(entry.getKey(), entry.getValue());
            }

        missesList.add(tempMap);

        symbolTable.clear();
        arrayDims.clear();
        arrayDatatypeBits.clear();

        loopIndexLevel.clear();
        loopIndexStride.clear();
        loopIndexLowerBound.clear();
        loopIndexUpperBound.clear();
        arrayDims.clear();
        arrayBlocks.clear();
        arrayRowCol.clear();
        arrayDatatypeBits.clear();
        blockElements.clear();
        cacheMisses.clear();
        //System.out.println("__________________________________________________");

    }
    // End of testcase
    @Override
    public void exitMethodDeclarator(LoopNestParser.MethodDeclaratorContext ctx) {
        //System.out.println("exitMethodDeclarator");
        //System.out.println("Text:  " + ctx.getText());
    }

    @Override public void enterTests(LoopNestParser.TestsContext ctx) {

    }
    @Override
    public void exitTests(LoopNestParser.TestsContext ctx) {
      /*
      for (int i = 0; i < missesList.size(); i++) {
        System.out.println("arraylist");
        System.out.println(missesList.get(i));
        for (Map.Entry<String, Long> entry : (missesList.get(i)).entrySet()) {
            System.out.println("Key = " + entry.getKey() +
                             ", Value = " + entry.getValue());
            //System.out.println("map");
            }
        System.out.println("__________________");
      }
*/
        try {
            FileOutputStream fos = new FileOutputStream("Results.obj");
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            // FIXME: Serialize your data to a file
            oos.writeObject(missesList);
            oos.close();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        //System.out.println("Text:  " + ctx.getText());
        //System.out.println("__________________________________________________");
    }

    @Override
    public void exitLocalVariableDeclaration(LoopNestParser.LocalVariableDeclarationContext ctx) {
      //System.out.println("exitLocalVariableDeclaration");
      //System.out.println("Text:  " + ctx.getText());

    }

    @Override
    public void exitIntegralType(LoopNestParser.IntegralTypeContext ctx) {
      exitVariableType = ctx.getText() ;
    }

    @Override
    public void exitVariableDeclarator(LoopNestParser.VariableDeclaratorContext ctx) {
      //System.out.println("exitVariableDeclarator");
      //System.out.println("Text:  " + ctx.getText());
      String declaration = ctx.getText();
      String[] parts = declaration.split("=");
      int dimCount = parts[1].length() - parts[1].replace("[", "").length();

      if (dimCount == 0) {
        if (parts[1].length() - parts[1].replace("\"", "").length() == 2) {
          if (parts[1].charAt(1) == 'D') {
            cacheType = 0;
          }
          else if (parts[1].charAt(1) == 'S') {
            cacheType = 1;
          }
          else if (parts[1].charAt(1) == 'F') {
            cacheType = 2;
          }
          else {
            cacheType = 0;
          }
        }
        else {
          if (symbolTable.containsKey(parts[1])) {
              symbolTable.put(parts[0], symbolTable.get(parts[1]));
          }
          else {
            symbolTable.put(parts[0], Integer.parseInt(parts[1]));
          }
        }
    }
    else {
      arrayDims.put(parts[0], dimCount);
      arrayDatatypeBits.put(parts[0], typeToSize.get(stringToType.get(exitVariableType)));
      parts[1] = parts[1].replace("[", "");
      parts[1] = parts[1].replace("newint", "");
      //System.out.println(parts[1]);
      String[] partstemp = parts[1].split("\\]");
      //for(int i=0; i < partstemp.length; i++) System.out.println("partstemp: " + partstemp[i]);
      if (symbolTable.containsKey(partstemp[0])) {
        if (dimCount == 1) arrayRowCol.put(parts[0], new ArrayList<Integer>() {{add(symbolTable.get(partstemp[0]));}} );
        else if (dimCount == 2) arrayRowCol.put(parts[0], new ArrayList<Integer>() {{add(symbolTable.get(partstemp[0])); add(symbolTable.get(partstemp[1]));}});
        else if (dimCount == 3) arrayRowCol.put(parts[0], new ArrayList<Integer>() {{add(symbolTable.get(partstemp[0])); add(symbolTable.get(partstemp[1])); add(symbolTable.get(partstemp[2]));}});
      }
      else {
        if (dimCount == 1) arrayRowCol.put(parts[0], new ArrayList<Integer>() {{add(Integer.parseInt(exitVariableValue));}} );
        else if (dimCount == 2) arrayRowCol.put(parts[0], new ArrayList<Integer>() {{add((Integer.parseInt(exitVariableValue))); add((Integer.parseInt(exitVariableValue)));}});
        else if (dimCount == 3) arrayRowCol.put(parts[0], new ArrayList<Integer>() {{add((Integer.parseInt(exitVariableValue))); add((Integer.parseInt(exitVariableValue))); add((Integer.parseInt(exitVariableValue)));}});
      }
    }

    }

    @Override
    public void exitArrayCreationExpression(LoopNestParser.ArrayCreationExpressionContext ctx) {
      //System.out.println("exitArrayCreationExpression");
      //System.out.println("Text:  " + ctx.getText());
    }

    @Override
    public void exitDimExprs(LoopNestParser.DimExprsContext ctx) {
      //System.out.println("exitDimExpr");
      //System.out.println("Text:  " + ctx.getText());
    }

    @Override
    public void exitDimExpr(LoopNestParser.DimExprContext ctx) {
      //System.out.println("exitDimExpr");
      //System.out.println("Text:  " + ctx.getText());
    }

    @Override
    public void exitLiteral(LoopNestParser.LiteralContext ctx) {
      //System.out.println("exitLiteral");
      //System.out.println("Text:  " + ctx.getText());
      exitVariableValue = ctx.getText();
    }

    @Override
    public void exitVariableDeclaratorId(LoopNestParser.VariableDeclaratorIdContext ctx) {
      //System.out.println("exitVariableDeclaratorId");
      //System.out.println("Text:  " + ctx.getText());
      exitVariableName = ctx.getText();
    }

    @Override
    public void exitUnannArrayType(LoopNestParser.UnannArrayTypeContext ctx) {
      //System.out.println("exitUnannArrayType");
      //System.out.println("Text:  " + ctx.getText());
    }

    @Override
    public void enterDims(LoopNestParser.DimsContext ctx) {
      //System.out.println("enterDims");
      //System.out.println("Text:  " + ctx.getText());
    }

    @Override
    public void exitUnannPrimitiveType(LoopNestParser.UnannPrimitiveTypeContext ctx) {
      //System.out.println("exitUnannPrimitiveType");
      //System.out.println("Text:  " + ctx.getText());
    }

    @Override
    public void exitNumericType(LoopNestParser.NumericTypeContext ctx) {
      //System.out.println("exitNumericType");
      //System.out.println("Text:  " + ctx.getText());
    }


    @Override
    public void exitFloatingPointType(LoopNestParser.FloatingPointTypeContext ctx) {
      //System.out.println("exitFloatingPointType");
      //System.out.println("Text:  " + ctx.getText());
      exitVariableType = ctx.getText();
    }

    @Override
    public void exitForInit(LoopNestParser.ForInitContext ctx) {
      //System.out.println("exitForInit");
      //System.out.println("Text:  " + ctx.getText());
      loopIndexLowerBound.put(exitVariableName, Integer.parseInt(exitVariableValue));
      loopIndexLevel.put(exitVariableName, nestingLevel);
    }

    @Override
    public void exitForCondition(LoopNestParser.ForConditionContext ctx) {
      //System.out.println("exitForCondition");
      //System.out.println("Text:  " + ctx.getText());
      String[] temp = ctx.getText().split("<");
      if (temp[1].charAt(0) == '=') {
        temp[1] = temp[1].replace("=", "");
        if (!symbolTable.containsKey(temp[1])) {
          symbolTable.put(temp[1], Integer.parseInt(temp[1]));
        }
        loopIndexUpperBound.put(exitVariableName, symbolTable.get(temp[1]));
      }
      else {
        if (!symbolTable.containsKey(temp[1])) {
          symbolTable.put(temp[1], Integer.parseInt(temp[1]));
        }
        loopIndexUpperBound.put(exitVariableName, symbolTable.get(temp[1])); //because < N, upperbound = N-1
      }
    }

    @Override
    public void exitRelationalExpression(LoopNestParser.RelationalExpressionContext ctx) {
      //System.out.println("exitRelationalExpression");
      //System.out.println("Text:  " + ctx.getText());
    }

    @Override
    public void exitForUpdate(LoopNestParser.ForUpdateContext ctx) {
      //System.out.println("exitForUpdate");
      //System.out.println("Text:  " + ctx.getText());
      String[] temp1 = ctx.getText().split("=");
      if (symbolTable.containsKey(temp1[1])) {
        loopIndexStride.put(exitVariableName, symbolTable.get(temp1[1]));
      }
      else {
        loopIndexStride.put(exitVariableName, Integer.parseInt(temp1[1]));
      }
    }

    @Override
    public void exitSimplifiedAssignment(LoopNestParser.SimplifiedAssignmentContext ctx) {
      //System.out.println("exitSimplifiedAssignment");
      //System.out.println("Text:  " + ctx.getText());
    }

    @Override
    public void exitArrayAccess(LoopNestParser.ArrayAccessContext ctx) {
      String[] temp2 = ctx.getText().split("\\[");
      //cacheMisses.put(temp2[0], 0L); //initialize the misses
      String[] dims = new String[arrayDims.get(temp2[0])]; //stores the index variables of array access
      long misses = 0;

      for (int i = 0; i < temp2.length-1; i++) {
          temp2[i+1] = temp2[i+1].replace("]", "");
          dims[i] = temp2[i+1];
      }

      int blockSize = (int)Math.pow(2, blockElements.get(temp2[0]));

      if (arrayDims.get(temp2[0]) == 1) { //1D array
        String accesslevel1 = dims[0];
        if (loopIndexStride.get(accesslevel1) <= blockSize) {
          misses = (loopIndexUpperBound.get(accesslevel1) - loopIndexLowerBound.get(accesslevel1)) / blockSize;
        }
        else {
          misses = ((loopIndexUpperBound.get(accesslevel1) - loopIndexLowerBound.get(accesslevel1)) / loopIndexStride.get(accesslevel1));
        }
    }

      else if (arrayDims.get(temp2[0]) == 2) { //2D array
        //int rowBlocks = arrayRowCol.get(temp2[0]).get(1) / blockElements.get(temp2[0]); //totalelementsinrow/nooflementsinablock
        String accesslevel1 = dims[0];
        String accesslevel2 = dims[1];

        if (loopIndexLevel.get(accesslevel1) < loopIndexLevel.get(accesslevel2)) { //row-wise access
          if (loopIndexStride.get(accesslevel1) <= blockSize) {
            misses =   ( ( ((loopIndexUpperBound.get(accesslevel1) - loopIndexLowerBound.get(accesslevel1)) ) *
                        ((loopIndexUpperBound.get(accesslevel2) - loopIndexLowerBound.get(accesslevel2)) / loopIndexStride.get(accesslevel2)) )  )
                        / blockSize;
          }
          else {
            misses =   ( ( ((loopIndexUpperBound.get(accesslevel1) - loopIndexLowerBound.get(accesslevel1)) / loopIndexStride.get(accesslevel1)) *
                        ((loopIndexUpperBound.get(accesslevel2) - loopIndexLowerBound.get(accesslevel2)) / loopIndexStride.get(accesslevel2)) ));
          }

        }
        else if (loopIndexLevel.get(accesslevel1) > loopIndexLevel.get(accesslevel2)) { //column-wise access
          if ( (loopIndexUpperBound.get(accesslevel1) - loopIndexLowerBound.get(accesslevel1)) / loopIndexStride.get(accesslevel1) <= cacheLines ) {
            misses =   ( ( ((loopIndexUpperBound.get(accesslevel1) - loopIndexLowerBound.get(accesslevel1)) / loopIndexStride.get(accesslevel1)) *
                        ((loopIndexUpperBound.get(accesslevel2) - loopIndexLowerBound.get(accesslevel2)) / loopIndexStride.get(accesslevel2)) )  )
                        / blockSize;
          }
          else {
            misses =   ( ((loopIndexUpperBound.get(accesslevel1) - loopIndexLowerBound.get(accesslevel1)) / loopIndexStride.get(accesslevel1)) *
                        ((loopIndexUpperBound.get(accesslevel2) - loopIndexLowerBound.get(accesslevel2)) / loopIndexStride.get(accesslevel2)) );

          }
        }
    }
    else {
      //3D array.
    }
    //System.out.println("Misses: " + misses);
    cacheMisses.put(temp2[0], misses);
}

    @Override
    public void exitExpressionName(LoopNestParser.ExpressionNameContext ctx) {
        String exitExpressionVariable = ctx.getText();
    }

    @Override
    public void exitArrayAccess_lfno_primary(LoopNestParser.ArrayAccess_lfno_primaryContext ctx) {
      String[] temp2 = ctx.getText().split("\\[");
      //cacheMisses.put(temp2[0], 0L); //initialize the misses
      String[] dims = new String[arrayDims.get(temp2[0])]; //stores the index variables of array access
      long misses = 0;

      for (int i = 0; i < temp2.length-1; i++) {
          temp2[i+1] = temp2[i+1].replace("]", "");
          dims[i] = temp2[i+1];
      }

      int blockSize = (int)Math.pow(2, blockElements.get(temp2[0]));

      if (arrayDims.get(temp2[0]) == 1) { //1D array
        String accesslevel1 = dims[0];
        /*
        System.out.println("accesslevel1: " + accesslevel1);
        System.out.println("accesslevel1UPPER: " + loopIndexUpperBound.get(accesslevel1));
        System.out.println("accesslevel1LOWER: " + loopIndexLowerBound.get(accesslevel1));
        System.out.println("accesslevel1STRIDE: " + loopIndexStride.get(accesslevel1));
        System.out.println("blockSize: " + blockSize);
        */
        if (loopIndexStride.get(accesslevel1) <= blockSize) {
          misses = (loopIndexUpperBound.get(accesslevel1) - loopIndexLowerBound.get(accesslevel1)) / blockSize;
        }
        else {
          misses = ((loopIndexUpperBound.get(accesslevel1) - loopIndexLowerBound.get(accesslevel1)) / loopIndexStride.get(accesslevel1));
        }
    }

      else if (arrayDims.get(temp2[0]) == 2) { //2D array
        //int rowBlocks = arrayRowCol.get(temp2[0]).get(1) / blockElements.get(temp2[0]); //totalelementsinrow/nooflementsinablock
        String accesslevel1 = dims[0];
        String accesslevel2 = dims[1];

        if (loopIndexLevel.get(accesslevel1) < loopIndexLevel.get(accesslevel2)) { //row-wise access
          if (loopIndexStride.get(accesslevel1) <= blockSize) {
            misses =   ( ( ((loopIndexUpperBound.get(accesslevel1) - loopIndexLowerBound.get(accesslevel1)) ) *
                        ((loopIndexUpperBound.get(accesslevel2) - loopIndexLowerBound.get(accesslevel2)) / loopIndexStride.get(accesslevel2)) )  )
                        / blockSize;
          }
          else {
            misses =   ( ( ((loopIndexUpperBound.get(accesslevel1) - loopIndexLowerBound.get(accesslevel1)) / loopIndexStride.get(accesslevel1)) *
                        ((loopIndexUpperBound.get(accesslevel2) - loopIndexLowerBound.get(accesslevel2)) / loopIndexStride.get(accesslevel2)) ));
          }

        }
        else if (loopIndexLevel.get(accesslevel1) > loopIndexLevel.get(accesslevel2)) { //column-wise access
          if ( (loopIndexUpperBound.get(accesslevel1) - loopIndexLowerBound.get(accesslevel1)) / loopIndexStride.get(accesslevel1) <= cacheLines ) {
            misses =   ( ( ((loopIndexUpperBound.get(accesslevel1) - loopIndexLowerBound.get(accesslevel1)) / loopIndexStride.get(accesslevel1)) *
                        ((loopIndexUpperBound.get(accesslevel2) - loopIndexLowerBound.get(accesslevel2)) / loopIndexStride.get(accesslevel2)) )  )
                        / blockSize;
          }
          else {
            misses =   ( ((loopIndexUpperBound.get(accesslevel1) - loopIndexLowerBound.get(accesslevel1)) / loopIndexStride.get(accesslevel1)) *
                        ((loopIndexUpperBound.get(accesslevel2) - loopIndexLowerBound.get(accesslevel2)) / loopIndexStride.get(accesslevel2)) );

          }
        }
    }
    else {
      //3D array.
    }
    //System.out.println("Misses: " + misses);
    cacheMisses.put(temp2[0], misses);
    }

    @Override
    public void exitForStatement(LoopNestParser.ForStatementContext ctx) {
        //System.out.println("exitForStatement");
        //System.out.println("Text:  " + ctx.getText());
      nestingLevel -= 1;
      /*
      System.out.println("loopIndexLowerBound");
        for (Map.Entry<String, Integer> entry : loopIndexLowerBound.entrySet())
            System.out.println("Key = " + entry.getKey() +
                             ", Value = " + entry.getValue());
      System.out.println("loopIndexUpperBound");
        for (Map.Entry<String, Integer> entry : loopIndexUpperBound.entrySet())
            System.out.println("Key = " + entry.getKey() +
                             ", Value = " + entry.getValue());
      System.out.println("loopIndexStride");
        for (Map.Entry<String, Integer> entry : loopIndexStride.entrySet())
            System.out.println("Key = " + entry.getKey() +
                             ", Value = " + entry.getValue());
       */
    }

    @Override
    public void enterForStatement(LoopNestParser.ForStatementContext ctx) {
      nestingLevel += 1;
      cacheLines = (int)Math.pow(2, symbolTable.get("cachePower") - symbolTable.get("blockPower")); //gives no of bits required to represnt cachelines

      //get number of sets in case of set associative mapping
      if (cacheType == 1) {
        sets = cacheLines - symbolTable.get("setSize");
      }

      if (nestingLevel == 1) {
        //find number of elements in a block for each Array.
        /*
        System.out.println("arrayDatatypeBits: ");
        for (Map.Entry<String, Integer> entry : arrayDatatypeBits.entrySet())
            System.out.println("Key = " + entry.getKey() +
                             ", Value = " + entry.getValue());
*/
        for (Map.Entry<String, Integer> entry : arrayDatatypeBits.entrySet()) {
            blockElements.put(entry.getKey(), symbolTable.get("blockPower") - entry.getValue() );
          }
/*
        System.out.println("blockElements: ");
        for (Map.Entry<String, Integer> entry : blockElements.entrySet())
            System.out.println("Key = " + entry.getKey() +
                             ", Value = " + entry.getValue());
*/
      }
    }
  }
