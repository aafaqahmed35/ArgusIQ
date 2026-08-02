import AppRoutes from './router'
import { TraceProvider } from './context/TraceContext'

function App() {
  return (
    <TraceProvider>
      <AppRoutes />
    </TraceProvider>
  )
}

export default App
