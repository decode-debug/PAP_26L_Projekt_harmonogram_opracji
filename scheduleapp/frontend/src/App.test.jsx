import React from 'react'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App'
import axios from 'axios'

vi.mock('axios')

const emptyGantt = {
  projectStart: null,
  projectEnd: null,
  totalDays: 0,
  bars: [],
}

function mockInitialLoads(operations = [], ganttData = emptyGantt, ganttLateData = emptyGantt) {
  axios.get.mockImplementation((url) => {
    if (url === '/api/operations') return Promise.resolve({ data: operations })
    if (url === '/api/operations/gantt') return Promise.resolve({ data: ganttData })
    if (url === '/api/operations/gantt-late') return Promise.resolve({ data: ganttLateData })
    return Promise.reject(new Error(`Unexpected GET ${url}`))
  })
}

describe('App', () => {
  afterEach(() => {
    cleanup()
  })

  beforeEach(() => {
    vi.clearAllMocks()
    window.confirm = vi.fn(() => true)
  })

  it('renders empty-state charts after loading without operations', async () => {
    mockInitialLoads()

    render(<App />)

    expect(screen.getByRole('heading', { name: 'Harmonogram Operacji' })).toBeInTheDocument()
    expect(await screen.findByText('Brak operacji w bazie danych.')).toBeInTheDocument()

    expect(screen.getAllByText('Brak operacji do wyświetlenia na wykresie.')).toHaveLength(2)
    expect(screen.getAllByText('Brak danych pracowników.')).toHaveLength(2)
  })

  it('renders operations fetched from the backend', async () => {
    mockInitialLoads([
      {
        id: 1,
        uuid: '11111111-1111-1111-1111-111111111111',
        name: 'Cięcie',
        startTime: '2026-04-20T08:00:00',
        endTime: '2026-04-21T08:00:00',
        workerCount: 2,
        resources: 'Piła',
        totalCost: 1200,
        crashingCostPerDay: 100,
        maxCrashingDays: 1,
        crashedDays: 0,
        asap: false,
        predecessorIds: '',
        durationInDays: 1,
      },
    ])

    render(<App />)

    expect((await screen.findAllByText('Cięcie')).length).toBeGreaterThan(0)
    expect(screen.getByText('Piła')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Usuń wszystkie' })).toBeInTheDocument()
  })

  it('switches the form into ASAP mode', async () => {
    const user = userEvent.setup()
    mockInitialLoads()

    render(<App />)

    await user.click(screen.getByRole('checkbox'))

    expect(screen.getByText('Czas trwania operacji:')).toBeInTheDocument()
    expect(screen.queryByText('Czas rozpoczęcia:')).not.toBeInTheDocument()
    expect(screen.queryByText('Czas zakończenia:')).not.toBeInTheDocument()
  })

  it('submits a standard operation and refreshes data', async () => {
    const user = userEvent.setup()
    mockInitialLoads()
    axios.post.mockResolvedValue({ data: { id: 10 } })

    render(<App />)

    fireEvent.change(getInputAfterLabel((await screen.findAllByText('Nazwa operacji:'))[0]), {
      target: { value: 'Spawanie' },
    })
    fireEvent.change(getInputAfterLabel(screen.getByText('Czas rozpoczęcia:')), {
      target: { value: '2026-04-20T08:00' },
    })
    fireEvent.change(getInputAfterLabel(screen.getByText('Czas zakończenia:')), {
      target: { value: '2026-04-21T08:00' },
    })
    fireEvent.change(getInputAfterLabel(screen.getByText('Liczba pracowników:')), {
      target: { value: '4' },
    })
    fireEvent.change(getInputAfterLabel(screen.getByText('Zasoby:')), {
      target: { value: 'Spawarka' },
    })

    fireEvent.click(screen.getByRole('button', { name: 'ZAPISZ OPERACJĘ' }))

    await waitFor(() => {
      expect(axios.post).toHaveBeenCalledWith('/api/operations', expect.objectContaining({
        name: 'Spawanie',
        asap: false,
        startTime: '2026-04-20T08:00',
        endTime: '2026-04-21T08:00',
        workerCount: 4,
        resources: 'Spawarka',
        predecessorIds: '',
      }))
    })

    expect(axios.get).toHaveBeenCalledWith('/api/operations')
    expect(axios.get).toHaveBeenCalledWith('/api/operations/gantt')
    expect(axios.get).toHaveBeenCalledWith('/api/operations/gantt-late')
  })
})

function getInputAfterLabel(labelElement) {
  const input = labelElement.nextElementSibling
  if (!input) throw new Error(`Missing input for label: ${labelElement.textContent}`)
  return input
}
