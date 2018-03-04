import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def helloWorld(){
    println "hello world"
}

boolean isAlive(Process p) {
    try {
        p.exitValue();
        return false;
    }
    catch (IllegalThreadStateException e) {
        return true;
    }
}


// wip - http://www.joergm.com/2010/09/executing-shell-commands-in-groovy/
def pyCommand(script, command) {

    // Write string to a temp file in the workspace
    writeFile(file: "tmp.py", text: command)
    fullPath = script.env.WORKSPACE + "/tmp.py"
    // fullPath = "c:/Program Files (x86)/Jenkins/workspace/test2/tmp.py"

    println "Executing:"
    println command

    ProcessBuilder builder = new ProcessBuilder([ "C:/Python27/python.exe", "-u", fullPath ]);
    builder.redirectErrorStream(true); // so we can ignore the error stream
    Process process = builder.start();
    InputStream out = process.getInputStream();
    OutputStream in = process.getOutputStream();

    byte[] buffer = new byte[4000];
    while (isAlive(process)) {
      int no = out.available();
      if (no > 0) {
        int n = out.read(buffer, 0, Math.min(no, buffer.length));
        System.out.println(new String(buffer, 0, n));
      }

      int ni = System.in.available();
      if (ni > 0) {
        int n = System.in.read(buffer, 0, Math.min(ni, buffer.length));
        in.write(buffer, 0, n);
        in.flush();
      }

      try {
        Thread.sleep(10);
      }
      catch (InterruptedException e) {
      }
    }

    System.out.println(process.exitValue());
    return
}



// #-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#
// MUST BE LAST
// #-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#
return this