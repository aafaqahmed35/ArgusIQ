import { Navigate, Route, Routes } from 'react-router-dom'
import AppShell from './components/layout/AppShell'
import Infrastructure from './pages/Infrastructure'
import Settings from './pages/Settings'
import Analytics from './pages/Analytics'
import TraceExplorer from './pages/TraceExplorer'
import Overview from './pages/Overview'
import Services from './pages/Services'
import Alerts from './pages/Alerts'

function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<Overview />} />
        <Route path="traces" element={<TraceExplorer />} />
        <Route path="analytics" element={<Analytics />} />
        <Route path="services" element={<Services />} />
        <Route path="alerts" element={<Alerts />} />
        <Route path="infrastructure" element={<Infrastructure />} />
        <Route path="settings" element={<Settings />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

export default AppRoutes
