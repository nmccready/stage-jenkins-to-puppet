#!/bin/sh
#PLEASE NOTE THIS FILE WAS CREATED BY A TEMPLATE!! TO MODIFY , modify the MODULE AND TEMPLATE!
applicationName=${1?missing application name}
stagePath=${2?missing stage path}
extension=${3?missing extension}
destinationAddress=${4?missing destination address}
extractCmd=${5?missing extraction command like "unzip"}
renameApplicationTo=${6:-$applicationName}
startName=${7:-'start'}

#do something in ssh land
ssh $destinationAddress applicationName=$applicationName stagePath=$stagePath extension=$extension destinationAddress=$destinationAddress extractCmd=$extractCmd renameApplicationTo=$renameApplicationTo startName=$startName 'bash -s' <<'ENDSSH'
# commands to run on remote host
######## Begin local hive replication #TODO - THIS IS PROBABLY being removed, to use git  as rollback
    newAppToBecomeCurrentApp=$renameApplicationTo'.new'$extension
    currentApp=$renameApplicationTo$extension
    originalBackupApp=$renameApplicationTo'.last'$extension
    rollBackApp=$renameApplicationTo'.last.bak'$extension
    
    echo 'stagePath: '$stagePath' on '$destinationAddress 
    #pwd
    cd $stagePath
    #move each app if it exists as a file
    if [ -f "$originalBackupApp" ]
    then
    	mv "$originalBackupApp" "$rollBackApp";
    fi
    
    if [ -f "$currentApp" ]
    then
    	mv "$currentApp" "$originalBackupApp";
    fi

    if [ -f "$newAppToBecomeCurrentApp" ]
    then
    	mv "$newAppToBecomeCurrentApp" "$currentApp";
    fi
  ######## End local hive replication

  ######## Begin Extraction
    #an extracted package is desirable for puppet management, since the extracted contents is pushed "as is"
    #creating an isolated location for unzipping the package
    extractDir=extractZone
    
    #always start with a fresh extraction zone
    if [ -d "$extractDir" ]
    then
      rm -rf $extractDir
    fi
    mkdir $extractDir
    
    #copy latest compressed app to extractZone and extract!
    cp $currentApp ./$extractDir
    cd $extractDir
    
    echo should be in extractZone
    pwd

    #extract
    extractWhole=$extractCmd' ./'$currentApp
    $extractWhole 

    #find the depth of packaging
    #move into matching directory there should only be one unziped applicationName at this point
    #wild cards in a command need to be created as a variable
    changeDirIntoZip=cd' '$applicationName'*'
    $changeDirIntoZip
    
    echo changed into "$applicationName" directory should be = $(pwd)
    #see if we need to go deeper
      if test -n "$(find $applicationName -maxdepth 1 -print -quit)"
      then
        echo found package one level deep moving to puppet modules
        
      else
        echo package is at zero depth, backing out and moving to puppet
        cd ../
      fi
    
      echo 'prior to cd "$applicationName" pwd'
      pwd
    rm -f *.zip

    ## Rename extracted to renameApplicationTo , always renaming since renameApplicationTo should default to applicationName
    mv ./"$applicationName" ./"$renameApplicationTo";
  ####### End Extraction

  ############# Actual Puppet Module Copying
  #clean up puppet module hive, and set up puppet module locations
  puppetModule=/etc/puppet/modules/"$renameApplicationTo"/files/stage
  removeOld=rm' -rf '$puppetModule/"$renameApplicationTo"'*'
  $removeOld
  
  #whatever the current naming convention it will be just appName in the end!
  copyAppToPuppetModule=cp' -r '"$renameApplicationTo"'* '"$puppetModule";
  #execute copy or move
  echo Copying via command $copyAppToPuppetModule
  $copyAppToPuppetModule
  
  echo 'App should be sent to puppet module @: '$puppetModule' as'"$renameApplicationTo"
ENDSSH
