export interface ParsedAlert {
  category:
    | 'AI Service Error'
    | 'Database Error'
    | 'Kafka Error'
    | 'Elasticsearch Error'
    | 'Redis Error'
    | 'Network Error'
    | 'Jira Integration Error'
    | 'Application Error';
  title: string;
  summary: string;
  rootCause: string;
  resolution: string[];
  statusCode?: string | number;
  exceptionClass?: string;
  isJson: boolean;
  jsonContent?: any;
  cleanMessage: string;
}

export function parseAlertMessage(message: string, serviceName: string): ParsedAlert {
  const cleanMessage = message.replace(/<EOL>/g, '\n').trim();

  // Initialize defaults
  let category: ParsedAlert['category'] = 'Application Error';
  let title = 'Application Alert';
  let summary = 'An unexpected alert occurred in the system.';
  let rootCause = 'The system generated an internal log warning or error.';
  let resolution = [
    'Inspect the service logs for related exception stack traces.',
    'Check the health status of dependent downstream microservices.'
  ];
  let statusCode: string | number | undefined = undefined;
  let exceptionClass: string | undefined = undefined;
  let isJson = false;
  let jsonContent: any = null;

  // Try to extract JSON from the message (usually at the end after colons/spaces)
  try {
    const jsonStart = cleanMessage.indexOf('{');
    const jsonEnd = cleanMessage.lastIndexOf('}');
    if (jsonStart !== -1 && jsonEnd !== -1 && jsonEnd > jsonStart) {
      const potentialJson = cleanMessage.substring(jsonStart, jsonEnd + 1);
      const parsed = JSON.parse(potentialJson);
      isJson = true;
      jsonContent = parsed;
    }
  } catch (e) {
    // Ignore, not valid JSON
  }

  // 1. AI Service Errors (Gemini)
  if (cleanMessage.includes('Gemini') || cleanMessage.includes('generativelanguage')) {
    category = 'AI Service Error';
    exceptionClass = 'GeminiAnalysisException';

    if (cleanMessage.includes('404 Not Found')) {
      title = 'Gemini API Model Not Found';
      summary = 'The AI analysis service attempted to access a model that does not exist or is unsupported.';
      statusCode = 404;
      
      // Attempt to extract the unsupported model name
      const modelMatch = cleanMessage.match(/models\/([a-zA-Z0-9.\-_]+)/);
      const modelName = modelMatch ? modelMatch[1] : 'gemini-1.5-flash';
      rootCause = `Model "${modelName}" is not supported or was not found under the configured API version.`;
      resolution = [
        'Check the configured Gemini model in target properties (e.g. gemini.api.model).',
        'Verify that you are using a supported Gemini model (e.g., gemini-2.5-flash or gemini-3.5-flash).',
        'Verify the Google AI Studio API version configurations (e.g. v1beta).'
      ];
    } else if (cleanMessage.includes('Read timed out') || cleanMessage.includes('SocketTimeoutException') || cleanMessage.includes('timeout')) {
      title = 'Gemini API Call Timeout';
      summary = 'The request to the Gemini API timed out during response generation.';
      statusCode = 'Timeout';
      rootCause = 'The reasoning phase (thinking tokens) took longer than the default HTTP read timeout.';
      resolution = [
        'Set thinkingBudget to 0 under thinkingConfig in the generationConfig to bypass the reasoning phase.',
        'Increase the readTimeout of the RestTemplate in GeminiAnalysisService (e.g. to 30000ms or 60000ms).',
        'Check network connectivity and Google API endpoint latency.'
      ];
    } else {
      title = 'Gemini AI Integration Error';
      summary = 'A failure occurred during AI analysis of a log event.';
      rootCause = 'Gemini API returned an error status or connection was interrupted.';
      resolution = [
        'Check configured API keys in the environment or properties file.',
        'Check Google API quota limits or service availability.'
      ];
    }
  }
  // 2. Jira Integration Errors
  else if (cleanMessage.includes('Jira') || cleanMessage.includes('JiraStory') || cleanMessage.includes('jira')) {
    category = 'Jira Integration Error';
    exceptionClass = 'JiraApiIntegrationException';
    statusCode = 400;

    if (cleanMessage.includes("project doesn't exist") || cleanMessage.includes("permission to create issues")) {
      title = 'Jira Project Permission Denied';
      summary = 'Failed to automatically trigger a Jira Story for the active alert.';
      rootCause = 'The target Jira project does not exist or the integration user has insufficient privileges to write to it.';
      resolution = [
        'Verify that the Jira project key configured in the notification service exists.',
        'Ensure the credentials (API token/User) have "Create Issues" permission in the project.',
        'Check the Jira integration endpoints and project credentials.'
      ];
    } else if (cleanMessage.includes('newline characters')) {
      title = 'Jira Invalid Summary Characters';
      summary = 'Failed to create Jira Story because the summary contains illegal newline characters.';
      rootCause = 'The issue summary generated from the log message contains line breaks, which is rejected by the Jira REST API.';
      resolution = [
        'Modify JiraStoryService to strip or replace newline characters in the summary field.',
        'Ensure the summary is formatted strictly on a single line.'
      ];
    } else if (cleanMessage.includes("exceed 255 characters")) {
      title = 'Jira Summary Too Long';
      summary = 'Failed to create Jira ticket because the summary exceeded the length constraint.';
      rootCause = "The generated issue summary exceeds Jira's maximum allowable limit of 255 characters.";
      resolution = [
        'Truncate the issue summary to 255 characters or less in JiraStoryService before making the request.',
        'Avoid embedding long exception messages directly inside the ticket title.'
      ];
    } else {
      title = 'Jira API Communication Error';
      summary = 'An error occurred when sending issue creation payload to Jira.';
      rootCause = 'Jira API returned a 400 Bad Request or connection failed.';
      resolution = [
        'Verify Jira endpoint base URLs and authentication tokens.',
        'Check the JSON request payload for schema compliance in JiraStoryService.'
      ];
    }
  }
  // 3. Database Errors (MongoDB / Redis)
  else if (
    cleanMessage.includes('Mongo') ||
    cleanMessage.includes('replica set') ||
    cleanMessage.includes('MongoDB') ||
    cleanMessage.includes('MongoClient')
  ) {
    category = 'Database Error';
    title = 'MongoDB Connection Failure';
    summary = 'Failed to perform database operations or connect to MongoDB clusters.';
    exceptionClass = cleanMessage.match(/(\w*Exception)/)?.[1] || 'MongoException';
    rootCause = 'MongoDB cluster node timed out, went offline, or failed replica set handshake.';
    resolution = [
      'Verify connection strings (host, port, credentials) in application configuration.',
      'Check if the MongoDB service is running locally or check MongoDB Atlas cluster status.',
      'Check database access lists (IP whitelist) in MongoDB security settings.'
    ];
  } else if (cleanMessage.includes('Redis') || cleanMessage.includes('redis') || cleanMessage.includes('Lettuce') || cleanMessage.includes('Jedis')) {
    category = 'Redis Error';
    title = 'Redis Cache Connection Error';
    summary = 'The microservice failed to connect to the Redis caching broker.';
    exceptionClass = 'RedisConnectionException';
    rootCause = 'Redis server is unreachable or timed out during key operations.';
    resolution = [
      'Verify the Redis container or local server is active on port 6379.',
      'Check Redis host configuration in application properties.',
      'Check system memory and network interface status.'
    ];
  }
  // 4. Elasticsearch Errors
  else if (cleanMessage.includes('Elasticsearch') || cleanMessage.includes('elastic') || cleanMessage.includes('ES METRICS')) {
    category = 'Elasticsearch Error';
    title = 'Elasticsearch Service Exception';
    summary = 'Failed to index logs or read metrics from Elasticsearch database.';
    exceptionClass = 'ElasticsearchException';
    rootCause = 'The query node returned an error, or the connection timed out.';
    resolution = [
      'Check if Elasticsearch node is running (port 9200) and healthy.',
      'Ensure index templates and ILM policies are correctly initialized.',
      'Check Elasticsearch logs for internal resource exhaustion or garbage collection freezes.'
    ];
  }
  // 5. Kafka Errors
  else if (cleanMessage.includes('Kafka') || cleanMessage.includes('ListenerContainer') || cleanMessage.includes('Consumer')) {
    category = 'Kafka Error';
    title = 'Kafka Messaging Broker Error';
    summary = 'Kafka consumer partition assignment or polling operations encountered a failure.';
    exceptionClass = 'KafkaException';
    rootCause = 'Broker connection was lost or consumer partition assignment failed.';
    resolution = [
      'Verify the Kafka cluster/broker is active on port 9092.',
      'Check consumer group-id configuration and auto-offset properties.',
      'Ensure target topics (e.g. app-logs) exist and are partitioned correctly.'
    ];
  }
  // 6. Network Errors
  else if (
    cleanMessage.includes('ConnectException') ||
    cleanMessage.includes('SocketTimeoutException') ||
    cleanMessage.includes('Connection refused') ||
    cleanMessage.includes('Mail server connection failed')
  ) {
    category = 'Network Error';
    title = 'Network Connection Failed';
    summary = 'A network connection request to an external service or broker failed.';
    exceptionClass = cleanMessage.match(/(\w*Exception)/)?.[1] || 'ConnectException';
    rootCause = 'Target socket port is unreachable, connection was refused, or timed out.';
    resolution = [
      'Verify hostnames and port numbers in the service configuration.',
      'Check local firewalls or security group rules.',
      'Confirm that the target service is running and accepting connection handshakes.'
    ];
  }
  // 7. General HTTP 500 Gateway Errors
  else if (cleanMessage.includes('500 Server Error')) {
    category = 'Application Error';
    const methodMatch = cleanMessage.match(/HTTP (GET|POST|PUT|DELETE) "([^"]+)"/);
    const method = methodMatch ? methodMatch[1] : '';
    const route = methodMatch ? methodMatch[2] : '';
    statusCode = 500;
    exceptionClass = 'HttpServerErrorException';

    if (route.includes('/api/logs')) {
      title = 'Logs Retrieval API Error';
      summary = `The gateway received a 500 Server Error when handling ${method} ${route}.`;
      rootCause = 'An internal database query exception occurred while fetching or filtering log events.';
      resolution = [
        'Check logs in the logservice module to see details of the query execution.',
        'Validate the date formats and query parameters passed in the REST request.',
        'Verify index mappings and field types in Elasticsearch.'
      ];
    } else if (route.includes('/api/alerts')) {
      title = 'Alerts Query API Error';
      summary = `An internal error occurred while retrieving active system alerts.`;
      rootCause = 'The notification service failed to read active triggers from memory or database.';
      resolution = [
        'Restart the notificationservice module to restore active message queue listeners.',
        'Verify MongoDB connection status and document states.'
      ];
    } else {
      title = `API Route HTTP 500: ${method} ${route}`;
      summary = `Request to ${route} failed with Internal Server Error.`;
      rootCause = 'The controller encountered an unhandled runtime exception during processing.';
      resolution = [
        'Review the backend application stack trace for unhandled NullPointer or database exceptions.',
        'Test the api endpoint locally to reproduce and debug the request payload.'
      ];
    }
  }

  // Final fallback refinements
  if (title === 'Application Alert') {
    // Try to extract an exception class name from the message
    const excMatch = cleanMessage.match(/([a-zA-Z0-9.]*(?:Exception|Error|Failure))\b/);
    if (excMatch) {
      exceptionClass = excMatch[1];
      title = exceptionClass.split('.').pop() || title;
      title = title.replace(/([A-Z])/g, ' $1').trim(); // split CamelCase
      summary = `A runtime ${title} was captured in the log stream.`;
    }
  }

  return {
    category,
    title,
    summary,
    rootCause,
    resolution,
    statusCode,
    exceptionClass,
    isJson,
    jsonContent,
    cleanMessage
  };
}
