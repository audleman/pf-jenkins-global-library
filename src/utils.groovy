import groovy.json.JsonSlurper
import groovy.json.JsonOutput

String prettyPrintAsJson(target) {
    JsonOutput.prettyPrint(JsonOutput.toJson(target))
}

def parseJson(String json) {
    return new JsonSlurper().parseText(json)
}

///
/// Python scripting utilities
///

// wip - http://www.joergm.com/2010/09/executing-shell-commands-in-groovy/
def pyCommand(String command, env) {

    // Write string to a temp file in the workspace
    writeFile(file: "tmp.py", text: command)
    fullPath = env.WORKSPACE + "/tmp.py"

    // fullPath = "c:/Program Files (x86)/Jenkins/workspace/test2/tmp.py"

    // Use the low-level process builder. This is so we can:
    // - capture regular output
    // - capture exit code so we know if an exception occurred
    // - redirect stderr to stdout so we can print a stack trace
    def process = new ProcessBuilder([ "C:/Python27/python.exe", "-u", fullPath ])
        .redirectErrorStream(true)
        .start()
    // Read output into a string builder
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
///
/// Powershell utilities -- try an make it easier to interface groovy with powershell
///


// Execute powershell script, expects script to write to stdout and
// returns that trimmed. For more fancy parsing use powershellInvoke
def powershellString(String script) {
    return powershell(returnStdout: true, script: script + '''
            exit 0
    ''').trim()
}

def powershellInvoke(String ps_script) {
    def json = powershell(returnStdout: true, script: '''
        try {
            $result = &{''' + ps_script + '''} | ConvertTo-Json -Depth 5
            if ($result) { return $result } else { return "null" }
        } catch {
            Write-Output $_.Exception | Format-List -Force
            throw
        }
        ''')
    return parseJson(json)
}

def powershellToolsScript(String scriptName, Map arguments) {
    argJson = JsonOutput.toJson(arguments)
    return powershellInvoke('''
        $argHashtable = @{}
        (\'''' + argJson + '''\'|ConvertFrom-Json).psobject.properties | foreach {$argHashtable[$_.Name]=$_.Value}
        $result = ./Deployment/tools/''' + scriptName + '''.ps1 @argHashtable
        return $result
        ''')
}


String normalizeCommit(String commit)
{
    //
    // Return the current commit of the branch checked out, allowing an 
    // override to be passed in. 
    // Formats to 7 characters, example: 229ce1e. This is used in all our build
    // resources, like S3 keys.
    //
    if (commit){
        println "Commit passed in: ${commit}"
        if (commit.length() > 7){
            commit = commit.substring(0, 7)
            println "Short commit: ${commit}"
        } else if (commit.length() < 7) {
            error "You must pass in 7 characters of the commit"
        }
    } else {
        commit = powershellInvoke("git log -n 1 --pretty=format:'%h'").trim().substring(0, 7)
        println "Using most recent commit: ${commit}"
    }
    return commit
}


boolean buildExists(String commit) {
    return powershellInvoke("""
        !! (Get-S3Object -BucketName "pf-build-ami-files" -KeyPrefix "deployment/${commit}/build-package/")
        """)
}


boolean verticalTemplateExists(String commit) {
    return powershellInvoke("""
        !! (Get-S3Object -BucketName "pf-build-ami-files" -Key "deployment/${commit}/vertical/cftemplate.json")
        """)
}

///
///  App stacks utilities
///

boolean amiMissing(String app) {
    return powershellInvoke('''
        $immutable = [bool]::Parse($env:Immutable)
        ./Deployment/tools/get_application_ami.ps1 `
            -vertical $env:Branch `
            -application ''' + app + ''' `
            -commit $env:Commit `
            -immutable_application_commit $immutable
        ''') == "missing"
}


boolean appWideStackMissing(String app){
    return powershellInvoke('''
        . ./Deployment/tools/utils.ps1
        $aws_profile = get_vertical_aws_profile $env:Branch
        $stack_name = "$env:Branch-vertical-''' + app + '''-application"
        $has_tmpl = Get-S3Object -BucketName pf-build-ami-files -Key "deployment/$env:commit/applications/''' + app + '''/cftemplate-app.json"
        if(!$has_tmpl){ return "False" }
        $stack = Get-CFNStackSummary -ProfileName $aws_profile CREATE_COMPLETE,UPDATE_COMPLETE,ROLLBACK_COMPLETE | ? { $_.StackName -eq $stack_name }
        return !$stack
    ''')
}   

///
///  Cloud stacks utilities
///

String getCloudProfileName(String cloud){
    return powershellString('''
        . ./Deployment/tools/utils.ps1
        get_cloud_aws_profile ''' + cloud
    )
}

def getCloudAppAndCommits(String cloud){
    def profileName = getCloudProfileName(cloud)
    // Returns a list of applications with name and commit. Has to work with an empty list because
    // a blank response from powershell will error out. Returning the string "null" is my best attempt so far
    out = powershell(returnStdout: true, script: '''
        $app_stacks = Get-CFNStack -ProfileName "''' + profileName + '''" |? {
            ($_.Tags |? { $_.Key -eq 'cloud' -and $_.Value -eq "''' + cloud + '''"}) -AND 
            ($_.Tags | ? { $_.Key -eq 'application' })}
        if (!$app_stacks) { return "null" }
        $app_stacks |% {$name=$_.StackName.Replace("''' + cloud + '''-", ""); $commit=($_.Tags |? {$_.Key -eq "commit"}).Value; "$name|$commit"}
    ''').trim()
    // This is my best attempt at returning a list in all cases. 
    if (out == "null"){
        return [] as String[]
    } else {
        return out.split("\n")
    }
}


def getCloudDataAndCommits(String cloud){
    def profileName = getCloudProfileName(cloud)
    out = powershell(returnStdout: true, script: '''
        $data_stacks = Get-CFNStack -ProfileName "''' + profileName + '''" |? {
            ($_.Tags |? { $_.Key -eq 'cloud' -and $_.Value -eq "''' + cloud + '''"}) -AND 
            ($_.Tags | ? { $_.Key -eq 'data' }) -AND
            ($_.ParentId -eq $null) }
        if (!$data_stacks){ return "null" }
        $data_stacks |% {$name=$_.StackName.Replace("''' + cloud + '''-", ""); $commit=($_.Tags |? {$_.Key -eq "commit"}).Value; "$name|$commit"} | Write-Output
    ''').trim()
    if (out == "null") {
        return [] as String[]
    } else {
        return out.split("\n")
    }
}



// #-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#
// MUST BE LAST
// #-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#-#
return this