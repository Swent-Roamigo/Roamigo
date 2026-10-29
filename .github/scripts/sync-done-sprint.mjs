const owner = "Swent-Roamigo";
const repo = "Roamigo";
const projectNumber = 1;
const issueNumber = Number(process.env.ISSUE_NUMBER);
const eventClosedAt = process.env.ISSUE_CLOSED_AT;
const token = process.env.GH_TOKEN;

if (!Number.isInteger(issueNumber) || !eventClosedAt || !token) {
  throw new Error("Missing issue number, closure time, or GitHub App token.");
}

const api = async (query, variables = {}) => {
  const response = await fetch("https://api.github.com/graphql", {
    method: "POST",
    headers: {
      authorization: `Bearer ${token}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({ query, variables }),
  });
  const body = await response.json();
  if (!response.ok || body.errors?.length) {
    throw new Error(`GitHub GraphQL request failed: ${JSON.stringify(body.errors ?? body)}`);
  }
  return body.data;
};

const configuration = await api(`
  query($owner: String!, $projectNumber: Int!, $repo: String!, $issueNumber: Int!) {
    organization(login: $owner) {
      projectV2(number: $projectNumber) {
        id
        fields(first: 100) {
          nodes {
            ... on ProjectV2IterationField {
              id
              name
              configuration {
                iterations { id title startDate duration }
                completedIterations { id title startDate duration }
              }
            }
            ... on ProjectV2SingleSelectField {
              id
              name
              options { id name }
            }
          }
        }
      }
      repository(name: $repo) {
        issue(number: $issueNumber) {
          state
          stateReason
          closedAt
          projectItems(first: 100) {
            nodes {
              id
              project { id }
            }
          }
        }
      }
    }
  }
`, { owner, projectNumber, repo, issueNumber });

const project = configuration.organization?.projectV2;
const issue = configuration.organization?.repository?.issue;
if (!project || !issue) throw new Error("Could not find organization project 1 or the issue.");
if (issue.state !== "CLOSED" || new Date(issue.closedAt).getTime() !== new Date(eventClosedAt).getTime()) {
  console.log("Issue is no longer in the state represented by this close event; skipping.");
  process.exit(0);
}

const item = issue.projectItems.nodes.find((candidate) => candidate.project.id === project.id);
if (!item) {
  console.log("Issue is not on organization project 1; skipping.");
  process.exit(0);
}

const fields = project.fields.nodes;
const sprintField = fields.find((field) => field.name === "Sprint" && field.configuration);
const statusField = fields.find((field) => field.name === "Status" && field.options);
if (!sprintField || !statusField) throw new Error("Project is missing the Sprint iteration or Status field.");

const allIterations = [
  ...sprintField.configuration.completedIterations,
  ...sprintField.configuration.iterations,
];
const iterationsByDate = allIterations.filter((iteration) => {
  const titleMatch = iteration.title.match(/^(?:Sprint\s*)?S?(\d+)$/i);
  if (!titleMatch) return false;
  const day = new Date(`${iteration.startDate}T00:00:00Z`);
  day.setUTCDate(day.getUTCDate() + iteration.duration);
  const endExclusive = day.toISOString().slice(0, 10);
  const closedDate = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/Zurich",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(eventClosedAt));
  return iteration.startDate <= closedDate && closedDate < endExclusive;
});

if (iterationsByDate.length !== 1) {
  throw new Error(`Expected one sprint for closure date ${eventClosedAt}, found ${iterationsByDate.length}.`);
}
const sprint = iterationsByDate[0];
const sprintNumber = sprint.title.match(/^(?:Sprint\s*)?S?(\d+)$/i)[1];
const doneOption = statusField.options.find((option) => option.name === `Done in S${sprintNumber}`);
if (!doneOption) throw new Error(`Status option "Done in S${sprintNumber}" does not exist.`);

const updateField = async (fieldId, value) => {
  await api(`
    mutation($projectId: ID!, $itemId: ID!, $fieldId: ID!, $value: ProjectV2FieldValue!) {
      updateProjectV2ItemFieldValue(input: {
        projectId: $projectId
        itemId: $itemId
        fieldId: $fieldId
        value: $value
      }) {
        projectV2Item { id }
      }
    }
  `, { projectId: project.id, itemId: item.id, fieldId, value });
};

await updateField(sprintField.id, { iterationId: sprint.id });
await updateField(statusField.id, { singleSelectOptionId: doneOption.id });
console.log(`Updated issue #${issueNumber}: Sprint ${sprintNumber}, Done in S${sprintNumber}.`);
