/**
 *
 *      ___         ___    
 *     /  /\       /  /\   
 *    /  /:/_     /  /:/   
 *   /  /:/ /\   /__/::\   
 *  /  /:/_/::\  \__\/\:\  
 * /__/:/__\/\:\    \  \:\ 
 * \  \:\ /~~/:/     \__\:\
 *  \  \:\  /:/      /  /:/
 *   \  \:\/:/      /__/:/ 
 *    \  \::/       \__\/  
 *     \__\/               
 * 
 * Copyright 2011 Nagai Masato
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
def VERSION = '11.05.05';

def jar = ''
def verbose = false
def srcdir = 'src'
def bundleGroovy = false
def libdirs = []
def workdir = 'gjwork'
def mainclass = ''
def manifestFile = ''
def copyResourceFiles = false
def java, javatarget, javasource
java = javatarget = javasource = System.getProperty('java.version').split('_')[0] 
def debug = 'off'

def cli = new CliBuilder(usage: 'groovy Gj.groovy [options] [jar]')
cli.with {
    s(longOpt: 'srcdir', args: 1, argName: 'dir', "Specify the source directory. The default is '${srcdir}'.")
    bg(longOpt: 'bundlegroovy', "Bundle the default Groovy on your system. The GROOVY_HOME environment variable is required.") 
    l(longOpt: 'libdirs', args: 1, argName: 'dir,dir,dir...', "Specify comma-separated list of library directories to bundle. The default is empty.")
    w(longOpt: 'workdir', args: 1, argName: 'dir', "Specify the work directory. This directory will be created on start and deleted on exit. The default is '${workdir}'.")
    m(longOpt: 'mainclass', args: 1, argName: 'class', "Specify the main class. This option is ignored when -mf/--manifestfile is specified.")
    j(longOpt: 'java', args: 1, argName: 'version', "Specify the java version that is used for both the target and the source level of javac. The default is '${java}'.")
    jt(longOpt: 'javatarget', args: 1, argName: 'version', "Specify the java version that is used for the target level of javac. The default is '${javatarget}'.")
    js(longOpt: 'javasource', args: 1, argName: 'version', "Specify the java version that is used for the source level of javac. The default is '${javasource}'.")
    d(longOpt: 'debug', "Compile source with debug information.")
    v(longOpt: 'verbose', 'Print messages about what this program doing.')
    h(longOpt: 'help', 'Print this message.')
    cf(longOpt: 'copyResourceFiles', 'Copy resource files into jar')
    ver(longOpt: 'version', 'Print the script version.')
}
def options = cli.parse(args)
if (!options) { return }
if (options.h) { cli.usage(); return }
if (options.ver) { println 'Gj version: ' + VERSION; return }
if (!options.arguments() || !(options.arguments()[0] =~ /.+\.jar/).matches()) {
    println 'specify the result jar file name correctly'
    return
}
options.with {
    jar = arguments()[0] 
    verbose = v
    mainclass = m
    bundleGroovy = bg
    if (s) { srcdir = s  }
    if (l) { libdirs = l.split(',') }
    if (w) { workdir = w }
    if (j) { javatarget = javasource = j }
    if (jt) { javatarget = jt }
    if (js) { javasource = js }
    if (d) { debug = 'on' }
    if (cf) { copyResourceFiles = true }
}

def log = { message, indent=0 -> 
    if (verbose) println '\t' * indent + message 
}

log 'Options:'
log 'jar=' + jar, 1
log 'srcdir=' + srcdir, 1
log 'bundlegroovy=' + bundleGroovy, 1 
log 'libdirs=' + ((libdirs) ? libdirs : 'empty'), 1 
log 'workdir=' + workdir, 1 
log 'mainclass=' + mainclass, 1
log 'javatarget=' + javatarget, 1
log 'javasource=' + javasource, 1
log 'debug=' + debug, 1
log 'copyResourceFiles=' + copyResourceFiles, 1
log ''


def ant = new AntBuilder()
ant.with {
    project.buildListeners.each { listener -> 
        if (listener instanceof org.apache.tools.ant.BuildLogger) {
            listener.messageOutputLevel = org.apache.tools.ant.Project.MSG_ERR
        }
    }
    log 'Create work directory'
    delete dir: workdir
    mkdir dir: workdir
    log 'Compile Groovy source files'
    sequential {
        taskdef name: 'groovyc', classname: 'org.codehaus.groovy.ant.Groovyc'
        groovyc srcdir: srcdir, destdir: workdir, {
            classpath {
                libdirs.each { libdir ->
                    fileset dir: libdir, includes: "*.jar" 
                }
                pathelement path: workdir
            }
            javac source: javasource, target: javatarget, debug: 'off'
        }
        copy(todir:workdir) {
            fileset(dir: srcdir) {
                exclude(name:"**/*.groovy")
            }
        }
    }
    log 'Create jar file'
    ant.jar destfile: jar, index: 'true', filesetmanifest: 'merge', {
        fileset dir: workdir
        if (bundleGroovy) {
            def GROOVY_HOME = new File(System.getenv('GROOVY_HOME'))
            if (!GROOVY_HOME.canRead()) {
                println 'set the GROOVY_HOME environment variable correctly.' 
                System.exit(0)
            }
            zipgroupfileset dir: GROOVY_HOME, includes: 'embeddable/*.jar'    
            zipgroupfileset dir: GROOVY_HOME, includes: 'lib/*.jar'
        }
        libdirs.each { libdir ->
            zipgroupfileset dir: libdir, includes: "*.jar"
        }
        if (mainclass) {
            manifest {
                attribute name: 'Main-Class', value: mainclass
            }
        }
    }
    
    if (libdirs) {
        log 'Remove signature files in libraries'
        def tempjar = 'temp.jar'
        zip destfile: tempjar, {
            zipfileset src: jar, excludes: 'META-INF/*.RSA, META-INF/*.DSA, META-INF/*.SF'
        }
        move file: tempjar, tofile: jar
    }
    log 'Remove work directory'
    delete dir: workdir
    
    log 'Done!'
}
