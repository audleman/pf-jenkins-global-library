import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def helloWorld(){
    println "hello world"
}


// wip - http://www.joergm.com/2010/09/executing-shell-commands-in-groovy/
def pyCommand(script, command) {

    // Write string to a temp file in the workspace
    writeFile(file: "tmp.py", text: command)
    fullPath = script.env.WORKSPACE + "/tmp.py"
    // fullPath = "c:/Program Files (x86)/Jenkins/workspace/test2/tmp.py"

    // Use the low-level process builder. This is so we can:
    // - capture regular output
    // - capture exit code so we know if an exception occurred
    // - redirect stderr to stdout so we can print a stack trace
    def process = new ProcessBuilder([ "C:/Python27/python.exe", "-u", fullPath ])
        .redirectErrorStream(true)
        .start()
    // // Read output into a string builder
    StringBuilder builder = new StringBuilder();
    process.inputStream.eachLine {
        builder.append(it)
        builder.append(System.getProperty("line.separator"));
    }
    process.waitFor();
    output = builder.toString()
    if (process.exitValue() != 0){
        throw new Exception(output)
    }
    // Success, return stdout
    return output 
     
}



// #-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#
// MUST BE LAST
// #-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#
return this