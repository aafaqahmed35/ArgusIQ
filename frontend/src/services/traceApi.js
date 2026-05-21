import axios from 'axios'

const API_BASE_URL = 'http://localhost:8080/api/v1'

const traceClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    Accept: 'application/json',
  },
})

export async function fetchTraces() {
  const response = await traceClient.get('/traces')
  return response.data
}
