#!/usr/bin/env bash
# Updates an already approved, dedicated teaching stack; never creates infrastructure.
set -euo pipefail
: "${STACK_NAME:?Set STACK_NAME}"
: "${AWS_REGION:?Set AWS_REGION}"
: "${IMAGE_TAG:?Set an immutable build tag}"
: "${COST_APPROVED:?Set COST_APPROVED=yes only after the owner approves AWS costs}"
[[ "$COST_APPROVED" == yes ]] || { echo 'AWS cost approval is required'; exit 1; }
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
outputs() { aws cloudformation describe-stacks --stack-name "$STACK_NAME" --query 'Stacks[0].Outputs' --output json; }
value() { outputs | jq -er --arg key "$1" '.[] | select(.OutputKey==$key) | .OutputValue'; }
update_stack() {
  local activate=$1
  aws cloudformation describe-stacks --stack-name "$STACK_NAME" --query 'Stacks[0].Parameters' --output json |
    jq --arg tag "$IMAGE_TAG" --arg activate "$activate" '[.[] | if .ParameterKey=="ImageTag" then {ParameterKey:.ParameterKey,ParameterValue:$tag} elif (.ParameterKey=="CoreCount" or .ParameterKey=="WorkerCount") and $activate=="yes" then {ParameterKey:.ParameterKey,ParameterValue:"1"} else {ParameterKey:.ParameterKey,UsePreviousValue:true} end]' > "$work/params.json"
  if ! aws cloudformation update-stack --stack-name "$STACK_NAME" --use-previous-template --capabilities CAPABILITY_IAM --parameters "file://$work/params.json" > "$work/update.out" 2> "$work/update.err"; then
    grep -q 'No updates are to be performed' "$work/update.err" || { cat "$work/update.err"; exit 1; }
  else
    aws cloudformation wait stack-update-complete --stack-name "$STACK_NAME"
  fi
}
update_stack no
cluster=$(value Cluster)
init=$(value InitTask)
subnets=$(value Subnets)
security=$(value TaskSecurity)
task=$(aws ecs run-task --cluster "$cluster" --task-definition "$init" --launch-type FARGATE --network-configuration "awsvpcConfiguration={subnets=[$subnets],securityGroups=[$security],assignPublicIp=ENABLED}" --output json | jq -er '.tasks[0].taskArn')
aws ecs wait tasks-stopped --cluster "$cluster" --tasks "$task"
aws ecs describe-tasks --cluster "$cluster" --tasks "$task" --output json | jq -e '.tasks[0].containers[0].exitCode==0' >/dev/null
update_stack yes
aws ecs wait services-stable --cluster "$cluster" --services "$(value CoreService)" "$(value WorkerService)"
BASE_URL=$(value Url) bash scripts/swe5001/smoke.sh
