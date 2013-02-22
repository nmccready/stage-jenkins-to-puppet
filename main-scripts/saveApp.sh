#!/bin/sh
#curl to add an application and a machine set
appName=${1?need application name}
versionExpected=${2?need expected value}
commaDelimitedMachineNameList=${3?missing comma delimmited machine name list}
serviceUrl=${4:-localhost:9000}
currentVersion=${5:-} #default none

machines=(${commaDelimitedMachineNameList//,/ })

curl_command(){
	app=$1 expected=$2 cluster=$3 url=$4
	echo $app $expected $cluster $url
	curl -v -H "Content-type:application/json" -X POST -d '{"name":"'"$app"'","expected":"'"$expected"'","actualCluster":['"$cluster]"'}' http://"$url"/apps/save
}

declare -a jsonReadyMachines
for i in "${!machines[@]}"
do
    if [ "$currentVersion" ]
    then
    	jsonReadyMachines["$i"]='{"machineName":"'"${machines["$i"]}"'","actual":"'"$currentVersion"'"},'
    else

    	jsonReadyMachines["$i"]='{"machineName":"'"${machines["$i"]}"'"},'
    fi
done
machineVersionsPaired="${jsonReadyMachines[*]}"
machinePairLastCommaRemoved="${machineVersionsPaired%?}"
curl_command "$appName" "$versionExpected" "$machinePairLastCommaRemoved" "$serviceUrl"