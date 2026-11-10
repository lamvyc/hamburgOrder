export type StoreBusinessStatus = 'OPEN' | 'CLOSED'

export interface Store {
  id: number
  code: string
  name: string
  address: string
  latitude: number
  longitude: number
  businessStatus: StoreBusinessStatus
  acceptingOrders: boolean
  orderable: boolean
  openingHours: string
}

export interface StoreListResponse {
  items: Store[]
}

export interface MenuProduct {
  id: number
  code: string
  name: string
  description: string
  price: string
}

export interface MenuCategory {
  code: string
  name: string
  products: MenuProduct[]
}

export interface MenuResponse {
  store: Store
  categories: MenuCategory[]
}
